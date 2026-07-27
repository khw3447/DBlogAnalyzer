package com.dbloganalyzer.log;

/** A single "SQL:" marked log entry, before any SQL-syntax analysis. */
public record RawSqlBlock(int sequence, String timestamp, String thread, String rawSql) {
}
