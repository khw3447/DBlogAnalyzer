from dbloganalyzer.sql_comments import CommentType, analyze


def test_classifies_leading_comment_as_statement_id():
    sql = "SELECT /* TSKECD4C2_getTSKECD4C2ForSelResult_0001 */ A.COL FROM A"

    comments = analyze(sql).comments

    assert len(comments) == 1
    assert comments[0].type == CommentType.STATEMENT_ID


def test_classifies_plus_prefixed_comment_as_hint():
    sql = "SELECT /*+ INDEX(A IDX_A) */ A.COL FROM A"

    comments = analyze(sql).comments

    assert len(comments) == 1
    assert comments[0].type == CommentType.HINT


def test_classifies_line_comment_after_a_value_as_line():
    sql = "INSERT INTO A (COL) VALUES ('K80' -- COL\n)"

    comments = analyze(sql).comments

    assert len(comments) == 1
    assert comments[0].type == CommentType.LINE


def test_does_not_treat_dashes_inside_string_literal_as_comment():
    sql = "SELECT * FROM A WHERE COL = '2026-07-24--not-a-comment'"

    result = analyze(sql)

    assert result.comments == []
    assert "2026-07-24--not-a-comment" in result.stripped


def test_stripped_text_removes_comments_but_keeps_sql_parsable():
    sql = "SELECT /* stmt-id */ /*+ INDEX(A IDX) */ A.COL -- trailing note\nFROM A"

    stripped = analyze(sql).stripped

    assert "SELECT" in stripped
    assert "A.COL" in stripped
    assert "stmt-id" not in stripped
    assert "INDEX" not in stripped
    assert "trailing note" not in stripped
