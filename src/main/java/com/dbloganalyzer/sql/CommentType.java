package com.dbloganalyzer.sql;

/**
 * Classification of a comment found inside a SQL statement.
 *
 * HINT      - Oracle optimizer hint, "/*+ ... *&#47;" (the '+' right after
 *             the opening "/*" is what distinguishes it from a plain block
 *             comment).
 * STATEMENT_ID - a plain block comment placed immediately after the SQL
 *             verb, matching the "/* mappedStatementId *&#47;" tag that
 *             iBatis injects when it logs the resolved SQL text.
 * BLOCK     - any other block comment.
 * LINE      - a "-- ..." line comment.
 */
public enum CommentType {
    HINT,
    STATEMENT_ID,
    BLOCK,
    LINE
}
