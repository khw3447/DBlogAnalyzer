package com.dbloganalyzer.log;

/**
 * A single "SQL:" marked log entry, before any SQL-syntax analysis.
 *
 * {@code guid} is the per-transaction id (거래일련번호) from the bracketed log
 * format's second field; it is empty for the legacy format, which has no
 * analogous id.
 */
public record RawSqlBlock(int sequence, String timestamp, String thread, String guid, String rawSql) {
}
