package com.dbloganalyzer.sql;

import java.util.List;

/** A fully analyzed SQL statement pulled out of the log. */
public record SqlRecord(
        int sequence,
        String timestamp,
        String thread,
        String guid,
        SqlType type,
        List<String> tables,
        List<ColumnValue> columnValues,
        List<CommentSpan> comments,
        String rawSql,
        boolean parsedOk,
        String parseError
) {
    public String statementIdTag() {
        return comments.stream()
                .filter(c -> c.type() == CommentType.STATEMENT_ID)
                .map(CommentSpan::text)
                .map(t -> t.substring(2, t.length() - 2).strip())
                .findFirst()
                .orElse("");
    }
}
