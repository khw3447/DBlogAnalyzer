"""Turns a raw "SQL:" log block into a fully analyzed SqlRecord.

Classifies its comments, determines the statement type, and - when the
text parses as valid SQL - lists the tables and column/value pairs it
touches. A statement sqlglot cannot parse is still reported (type detected
by keyword, no table/column detail) rather than dropped, since a single
unusual statement should not hide the rest of the analysis.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum, auto

import sqlglot
from sqlglot import exp

from .logparser import RawSqlBlock
from .sql_comments import CommentSpan, CommentType, analyze as analyze_comments

_LEADING_VERB = re.compile(r"^(SELECT|INSERT|UPDATE|DELETE|MERGE)\b", re.IGNORECASE)

_OPERATOR_SYMBOLS: dict[type, str] = {
    exp.EQ: "=",
    exp.NEQ: "!=",
    exp.GT: ">",
    exp.GTE: ">=",
    exp.LT: "<",
    exp.LTE: "<=",
    exp.Like: "LIKE",
}
_COMPARISON_TYPES = tuple(_OPERATOR_SYMBOLS) + (exp.In,)


class SqlType(Enum):
    SELECT = auto()
    INSERT = auto()
    UPDATE = auto()
    DELETE = auto()
    MERGE = auto()
    OTHER = auto()


_TYPE_BY_KEYWORD = {
    "SELECT": SqlType.SELECT,
    "INSERT": SqlType.INSERT,
    "UPDATE": SqlType.UPDATE,
    "DELETE": SqlType.DELETE,
    "MERGE": SqlType.MERGE,
}

_TYPE_BY_EXPRESSION: dict[type, SqlType] = {
    exp.Select: SqlType.SELECT,
    exp.Insert: SqlType.INSERT,
    exp.Update: SqlType.UPDATE,
    exp.Delete: SqlType.DELETE,
    exp.Merge: SqlType.MERGE,
}


@dataclass(frozen=True)
class ColumnValue:
    clause: str
    table: str
    column: str
    operator: str
    value: str


@dataclass(frozen=True)
class SqlRecord:
    sequence: int
    timestamp: str
    thread: str
    type: SqlType
    tables: list[str]
    column_values: list[ColumnValue]
    comments: list[CommentSpan]
    raw_sql: str
    parsed_ok: bool
    parse_error: str | None

    def statement_id_tag(self) -> str:
        for c in self.comments:
            if c.type == CommentType.STATEMENT_ID:
                return c.text[2:-2].strip()
        return ""


def analyze(block: RawSqlBlock) -> SqlRecord:
    analyzed = analyze_comments(block.raw_sql)
    keyword_type = _detect_type_by_keyword(analyzed.stripped)

    try:
        statement = sqlglot.parse_one(analyzed.stripped, read="oracle")
        sql_type = _TYPE_BY_EXPRESSION.get(type(statement), keyword_type)
        tables = _extract_tables(statement)
        column_values = _extract_column_values(statement)
        return SqlRecord(block.sequence, block.timestamp, block.thread, sql_type, tables,
                          column_values, analyzed.comments, block.raw_sql, True, None)
    except Exception as e:  # sqlglot raises various ParseError/TokenizeError subclasses
        return SqlRecord(block.sequence, block.timestamp, block.thread, keyword_type, [],
                          [], analyzed.comments, block.raw_sql, False, str(e))


def _detect_type_by_keyword(sql: str) -> SqlType:
    m = _LEADING_VERB.match(sql.lstrip())
    if not m:
        return SqlType.OTHER
    return _TYPE_BY_KEYWORD.get(m.group(1).upper(), SqlType.OTHER)


def _extract_tables(statement: exp.Expression) -> list[str]:
    seen: dict[str, None] = {}
    for table in statement.find_all(exp.Table):
        seen[exp.table_name(table)] = None
    return list(seen)


def _extract_column_values(statement: exp.Expression) -> list[ColumnValue]:
    out: list[ColumnValue] = []

    for where in statement.find_all(exp.Where):
        out.extend(_collect_condition(where.this, "WHERE"))
    for join in statement.find_all(exp.Join):
        on = join.args.get("on")
        if on is not None:
            out.extend(_collect_condition(on, "ON"))

    if isinstance(statement, exp.Update):
        out.extend(_collect_set(statement.expressions, "SET"))
    elif isinstance(statement, exp.Insert):
        out.extend(_collect_insert(statement))
    elif isinstance(statement, exp.Merge):
        out.extend(_collect_merge(statement))

    return out


def _collect_condition(condition: exp.Expression, clause: str) -> list[ColumnValue]:
    out: list[ColumnValue] = []
    for node in condition.find_all(_COMPARISON_TYPES):
        if isinstance(node, exp.In):
            if isinstance(node.this, exp.Column):
                value = ", ".join(_literal_text(v) for v in node.expressions)
                out.append(ColumnValue(clause, _col_table(node.this), node.this.name, "IN", value))
            continue

        operator = _OPERATOR_SYMBOLS[type(node)]
        left, right = node.this, node.expression
        # Accept any right-hand side that isn't itself a column reference - a plain
        # literal, but also function calls like TO_CHAR(SYSDATE, 'YYYYMMDD') or
        # NVL(...), which used to be silently dropped (and so were unsearchable)
        # because only Literal/Null nodes were treated as a capturable "value".
        if isinstance(left, exp.Column) and not isinstance(right, exp.Column):
            out.append(ColumnValue(clause, _col_table(left), left.name, operator, _literal_text(right)))
        elif isinstance(right, exp.Column) and not isinstance(left, exp.Column):
            out.append(ColumnValue(clause, _col_table(right), right.name, operator, _literal_text(left)))
    return out


def _collect_set(assignments: list[exp.Expression], clause: str) -> list[ColumnValue]:
    out: list[ColumnValue] = []
    for eq in assignments:
        if isinstance(eq, exp.EQ) and isinstance(eq.this, exp.Column):
            out.append(ColumnValue(clause, _col_table(eq.this), eq.this.name, "=", _literal_text(eq.expression)))
    return out


def _collect_insert(insert: exp.Insert) -> list[ColumnValue]:
    target = insert.this
    if not isinstance(target, exp.Schema):
        return []  # no explicit column list; can't reliably pair positions

    values_expr = insert.expression
    if not isinstance(values_expr, exp.Values) or not values_expr.expressions:
        return []

    first_row = values_expr.expressions[0]
    value_list = first_row.expressions if isinstance(first_row, exp.Tuple) else [first_row]

    out: list[ColumnValue] = []
    for name_node, value_node in zip(target.expressions, value_list):
        out.append(ColumnValue("VALUES", "", name_node.name, "=", _literal_text(value_node)))
    return out


def _collect_merge(merge: exp.Merge) -> list[ColumnValue]:
    out: list[ColumnValue] = []
    on = merge.args.get("on")
    if on is not None:
        out.extend(_collect_condition(on, "ON"))

    whens = merge.args.get("whens")
    for when in (whens.expressions if whens is not None else []):
        then = when.args.get("then")
        if isinstance(then, exp.Update):
            out.extend(_collect_set(then.expressions, "SET"))
        elif isinstance(then, exp.Insert):
            columns = then.this.expressions if isinstance(then.this, exp.Tuple) else []
            values = then.expression.expressions if isinstance(then.expression, exp.Tuple) else []
            for name_node, value_node in zip(columns, values):
                out.append(ColumnValue("VALUES", "", name_node.name, "=", _literal_text(value_node)))
    return out


def _col_table(column: exp.Column) -> str:
    return column.table or ""


def _literal_text(e: exp.Expression) -> str:
    if isinstance(e, exp.Null):
        return "NULL"
    if isinstance(e, exp.Literal):
        return str(e.this)
    # sqlglot normalizes many dialect-specific functions into its own canonical
    # expressions (e.g. Oracle's TO_CHAR(d, fmt) becomes TimeToStr internally),
    # so rendering without a dialect would print that generic form (TIME_TO_STR)
    # instead of the Oracle syntax the log actually contains.
    return e.sql(dialect="oracle")
