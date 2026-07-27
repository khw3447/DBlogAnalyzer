package com.dbloganalyzer.log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw batch-job log text into individual SQL blocks.
 *
 * Two log line prefix formats are recognized, matched in this order:
 *
 * <ul>
 *   <li>Legacy: {@code yyyy-MM-dd HH:mm:ss,SSS LEVEL thread logger - message}</li>
 *   <li>Bracketed: {@code [yyyy-MM-dd HH:mm:ss][txnId][programCode][LEVEL]message}
 *       (no millis, no dash; seen in KB05023xxx-style transaction logs)</li>
 * </ul>
 *
 * In both formats, a SQL statement is logged as a "SQL:" marker message
 * followed by the fully resolved SQL text (bind values already inlined)
 * spanning an arbitrary number of un-prefixed lines, until the next
 * prefixed log line (in either format) appears.
 */
public class LogParser {

    private static final Pattern LOG_PREFIX_LEGACY = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+-\\s?(.*)$");

    private static final Pattern LOG_PREFIX_BRACKETED = Pattern.compile(
            "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s*]\\s*\\[([^]]*)]\\s*\\[([^]]*)]\\s*\\[\\s*(\\w+)\\s*]\\s*(.*)$");

    private static final Pattern SQL_MARKER = Pattern.compile("^SQL\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    /** A recognized log-prefix line, normalized across the formats above. */
    private record PrefixMatch(String timestamp, String thread, String message) {
    }

    private PrefixMatch matchPrefix(String line) {
        Matcher legacy = LOG_PREFIX_LEGACY.matcher(line);
        if (legacy.matches()) {
            return new PrefixMatch(legacy.group(1), legacy.group(3), legacy.group(5));
        }
        Matcher bracketed = LOG_PREFIX_BRACKETED.matcher(line);
        if (bracketed.matches()) {
            // group(2) is the long per-transaction id, group(3) the calling program/screen
            // code - the latter is the closer analogue of "thread" for display purposes.
            return new PrefixMatch(bracketed.group(1), bracketed.group(3), bracketed.group(5));
        }
        return null;
    }

    public List<RawSqlBlock> extractSqlBlocks(String logText) {
        List<RawSqlBlock> blocks = new ArrayList<>();
        String[] lines = logText.split("\r\n|\r|\n", -1);

        String currentTimestamp = null;
        String currentThread = null;
        StringBuilder currentSql = null;
        int sequence = 0;

        for (String line : lines) {
            PrefixMatch prefix = matchPrefix(line);
            if (prefix != null) {
                if (currentSql != null) {
                    blocks.add(new RawSqlBlock(++sequence, currentTimestamp, currentThread, currentSql.toString().strip()));
                    currentSql = null;
                }

                Matcher sqlMarker = SQL_MARKER.matcher(prefix.message().strip());
                if (sqlMarker.matches()) {
                    currentTimestamp = prefix.timestamp();
                    currentThread = prefix.thread();
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
