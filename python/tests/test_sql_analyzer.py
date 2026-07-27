from pathlib import Path

from dbloganalyzer.logparser import RawSqlBlock, extract_sql_blocks
from dbloganalyzer.sql_analyzer import CommentType, SqlType, analyze

FIXTURE = (Path(__file__).parent / "fixtures" / "sample-log.txt").read_text(encoding="utf-8")
BRACKET_FIXTURE = (Path(__file__).parent / "fixtures" / "sample-log-bracket.txt").read_text(encoding="utf-8")


def load_blocks():
    return extract_sql_blocks(FIXTURE)


def load_bracket_blocks():
    return extract_sql_blocks(BRACKET_FIXTURE)


def test_select_statement_finds_outer_and_subquery_tables_and_statement_id_tag():
    record = analyze(load_blocks()[0])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.SELECT
    assert "INST1.TSKECD4C2" in record.tables
    assert "INST1.TSKECD4C4" in record.tables
    assert record.statement_id_tag() == "TSKECD4C2_getTSKECD4C2ForSelResult_0001"
    assert any(c.type == CommentType.HINT for c in record.comments)
    assert any(cv.column == "배당지급구분" and cv.value == "2" for cv in record.column_values)
    assert any(cv.column == "그룹회사코드" and cv.value == "K80" for cv in record.column_values)


def test_update_statement_extracts_set_and_where_column_values():
    record = analyze(load_blocks()[1])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.UPDATE
    assert record.tables == ["INST1.TSKECOPKO"]
    assert any(cv.clause == "SET" and cv.column == "배도금액" and cv.value == "36780418"
               for cv in record.column_values)
    assert any(cv.clause == "WHERE" and cv.column == "운용기관관리번호" and cv.value == "24442594"
               for cv in record.column_values)


def test_insert_statement_pairs_columns_with_values_ignoring_trailing_line_comments():
    record = analyze(load_blocks()[2])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.INSERT
    assert record.tables == ["INST1.TSKECPK32"]
    assert any(cv.column == "자산관리기관코드" and cv.value == "B004" for cv in record.column_values)
    assert sum(1 for c in record.comments if c.type == CommentType.LINE) >= 5


def test_delete_statement_detected():
    record = analyze(load_blocks()[3])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.DELETE
    assert record.tables == ["INST1.TSKECD4C2"]


def test_merge_statement_detected_with_all_tables():
    record = analyze(load_blocks()[4])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.MERGE
    assert "INST1.TSKECOPKO" in record.tables


def test_bracketed_format_select_parses_correctly():
    record = analyze(load_bracket_blocks()[0])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.SELECT
    assert record.tables == ["INST1.TSKECC101"]
    assert record.thread == "KEC0649142"
    assert any(cv.column == "그룹회사코드" and cv.value == "KB0" for cv in record.column_values)


def test_bracketed_format_update_parses_correctly():
    record = analyze(load_bracket_blocks()[1])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.UPDATE
    assert record.tables == ["INST1.TSKECC201"]
    assert any(cv.clause == "SET" and cv.column == "퇴직연금거래결과구분" and cv.value == "B"
               for cv in record.column_values)


def test_bracketed_format_lowercase_insert_with_multiline_xml_literal_parses_correctly():
    record = analyze(load_bracket_blocks()[2])

    assert record.parsed_ok, record.parse_error
    assert record.type == SqlType.INSERT
    assert record.tables == ["inst1.TSKSAST04"]
    assert any(cv.column == "시스템경로번호" and cv.value == "002" for cv in record.column_values)


def test_where_clause_function_call_value_is_captured():
    block = RawSqlBlock(1, "2026-01-01 00:00:00,000", "t",
                         "SELECT 1 FROM DUAL WHERE 가입년월일 <= TO_CHAR(SYSDATE, 'YYYYMMDD') "
                         "AND 해제년월일 = NVL(종료일자, '99991231')")
    record = analyze(block)

    assert record.parsed_ok, record.parse_error
    assert any(cv.column == "가입년월일" and "TO_CHAR" in cv.value and "SYSDATE" in cv.value
               for cv in record.column_values)
    assert any(cv.column == "해제년월일" and "NVL" in cv.value for cv in record.column_values)
