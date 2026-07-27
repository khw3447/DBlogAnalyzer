package com.dbloganalyzer.log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw batch-job log text into individual SQL blocks.
 *
 * The observed log format prefixes every log entry with
 * "yyyy-MM-dd HH:mm:ss,SSS LEVEL thread logger - message", and a SQL
 * statement is logged as a "SQL:" marker line followed by the fully
 * resolved SQL text (bind values already inlined) spanning an arbitrary
 * number of un-prefixed lines, until the next prefixed log line appears.
 */
public class LogParser {

    private static final Pattern LOG_PREFIX = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+-\\s?(.*)$");

    private static final Pattern SQL_MARKER = Pattern.compile("^SQL\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    public List<RawSqlBlock> extractSqlBlocks(String logText) {
        List<RawSqlBlock> blocks = new ArrayList<>();
        String[] lines = logText.split("\r\n|\r|\n", -1);

        String currentTimestamp = null;
        String currentThread = null;
        StringBuilder currentSql = null;
        int sequence = 0;

        for (String line : lines) {
            Matcher prefixMatcher = LOG_PREFIX.matcher(line);
            if (prefixMatcher.matches()) {
                if (currentSql != null) {
                    blocks.add(new RawSqlBlock(++sequence, currentTimestamp, currentThread, currentSql.toString().strip()));
                    currentSql = null;
                }

                String message = prefixMatcher.group(5);
                Matcher sqlMarker = SQL_MARKER.matcher(message.strip());
                if (sqlMarker.matches()) {
                    currentTimestamp = prefixMatcher.group(1);
                    currentThread = prefixMatcher.group(3);
                    currentSql = new StringBuilder();
                    String inline = sqlMarker.group(1);
                    if (!inline.isBlank()) {
                        currentSql.append(inline).append('\n');
                    }
                }
            } else if (currentSql != null) {
                currentSql.append(line).append('\n');
            }
        }

        if (currentSql != null) {
            blocks.add(new RawSqlBlock(++sequence, currentTimestamp, currentThread, currentSql.toString().strip()));
        }

        return blocks;
    }
}
