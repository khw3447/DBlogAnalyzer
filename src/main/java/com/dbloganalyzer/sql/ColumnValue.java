package com.dbloganalyzer.sql;

/**
 * One column reference extracted from a SQL statement.
 *
 * @param clause   which part of the statement it came from ("WHERE", "SET", "VALUES", "ON")
 * @param table    owning table/alias, when it could be determined ("" if unknown)
 * @param column   column name
 * @param operator comparison/assignment operator ("=", ">", "IN", ...)
 * @param value    literal value as written in the SQL (already bind-resolved in this log format)
 */
public record ColumnValue(String clause, String table, String column, String operator, String value) {
}
