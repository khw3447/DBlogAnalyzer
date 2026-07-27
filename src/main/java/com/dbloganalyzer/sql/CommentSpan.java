package com.dbloganalyzer.sql;

/** A comment found in a SQL statement, with its position in the original text. */
public record CommentSpan(int start, int end, CommentType type, String text) {
}
