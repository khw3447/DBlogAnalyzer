package com.dbloganalyzer.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperation;
import net.sf.jsqlparser.statement.merge.MergeUpdate;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls "table.column = value" style entries out of a parsed statement: the
 * WHERE clause for SELECT/UPDATE/DELETE, the SET clause for UPDATE, the
 * column/VALUES pairing for INSERT, and the ON/insert/update parts of a
 * MERGE. SELECT statements are also walked into every subquery reachable
 * through FROM/JOIN, since this codebase's SQL nests a filtered subquery
 * inside the outer query rather than using an ANSI JOIN.
 */
final class ColumnValueExtractor {

    private ColumnValueExtractor() {
    }

    static List<ColumnValue> extract(Statement statement) {
        List<ColumnValue> out = new ArrayList<>();
        if (statement instanceof Select select) {
            collectFromSelect(select, out);
        } else if (statement instanceof Update update) {
            collectFromUpdateSets(update.getUpdateSets(), "SET", out);
            addWhere(update.getWhere(), "WHERE", out);
        } else if (statement instanceof Insert insert) {
            collectFromInsert(insert, out);
        } else if (statement instanceof Delete delete) {
            addWhere(delete.getWhere(), "WHERE", out);
        } else if (statement instanceof Merge merge) {
            collectFromMerge(merge, out);
        }
        return out;
    }

    private static void collectFromSelect(Select select, List<ColumnValue> out) {
        if (select instanceof PlainSelect plainSelect) {
            addWhere(plainSelect.getWhere(), "WHERE", out);
            if (plainSelect.getFromItem() != null) {
                collectFromFromItem(plainSelect.getFromItem(), out);
            }
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    collectFromFromItem(join.getRightItem(), out);
                    if (join.getOnExpressions() != null) {
                        for (Expression on : join.getOnExpressions()) {
                            addWhere(on, "ON", out);
                        }
                    }
                }
            }
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select part : setOperationList.getSelects()) {
                collectFromSelect(part, out);
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect) {
            collectFromSelect(parenthesedSelect.getSelect(), out);
        }
    }

    private static void collectFromFromItem(FromItem item, List<ColumnValue> out) {
        if (item instanceof ParenthesedSelect parenthesedSelect) {
            collectFromSelect(parenthesedSelect.getSelect(), out);
        } else if (item instanceof ParenthesedFromItem parenthesedFromItem) {
            collectFromFromItem(parenthesedFromItem.getFromItem(), out);
        }
    }

    private static void collectFromUpdateSets(List<UpdateSet> updateSets, String clause, List<ColumnValue> out) {
        if (updateSets == null) {
            return;
        }
        for (UpdateSet set : updateSets) {
            List<Column> columns = set.getColumns();
            List<?> values = set.getValues();
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression value = i < values.size() ? (Expression) values.get(i) : null;
                String valueText = value == null ? "" : LiteralFormatter.text(value);
                out.add(new ColumnValue(clause, "", LiteralFormatter.unquoteIdentifier(column.getColumnName()), "=", valueText));
            }
        }
    }

    private static void collectFromInsert(Insert insert, List<ColumnValue> out) {
        List<Column> columns = insert.getColumns();
        if (columns == null) {
            return;
        }
        Values values = insert.getValues();
        List<?> valueExprs = values != null ? values.getExpressions() : List.of();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            Expression value = i < valueExprs.size() ? (Expression) valueExprs.get(i) : null;
            String valueText = value == null ? "" : LiteralFormatter.text(value);
            out.add(new ColumnValue("VALUES", "", LiteralFormatter.unquoteIdentifier(column.getColumnName()), "=", valueText));
        }
    }

    private static void collectFromMerge(Merge merge, List<ColumnValue> out) {
        addWhere(merge.getOnCondition(), "ON", out);
        for (MergeOperation operation : merge.getOperations()) {
            if (operation instanceof MergeUpdate mergeUpdate) {
                collectFromUpdateSets(mergeUpdate.getUpdateSets(), "SET", out);
                addWhere(mergeUpdate.getWhereCondition(), "WHERE", out);
            } else if (operation instanceof MergeInsert mergeInsert) {
                List<Column> columns = mergeInsert.getColumns();
                List<?> values = mergeInsert.getValues();
                if (columns != null && values != null) {
                    for (int i = 0; i < columns.size(); i++) {
                        Column column = columns.get(i);
                        Expression value = i < values.size() ? (Expression) values.get(i) : null;
                        String valueText = value == null ? "" : LiteralFormatter.text(value);
                        out.add(new ColumnValue("VALUES", "", LiteralFormatter.unquoteIdentifier(column.getColumnName()), "=", valueText));
                    }
                }
                addWhere(mergeInsert.getWhereCondition(), "WHERE", out);
            }
        }
    }

    private static void addWhere(Expression expression, String clause, List<ColumnValue> out) {
        if (expression == null) {
            return;
        }
        WhereColumnValueVisitor visitor = new WhereColumnValueVisitor(clause);
        expression.accept(visitor);
        out.addAll(visitor.results());
    }
}
