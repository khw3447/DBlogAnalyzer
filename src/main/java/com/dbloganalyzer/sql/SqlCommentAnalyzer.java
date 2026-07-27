package com.dbloganalyzer.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans a raw SQL string and separates its three comment flavors:
 *
 * <ul>
 *   <li>Oracle optimizer hints: {@code /*+ ... *}{@code /} - the '+' right
 *       after the opening "/*" is the distinguishing marker.</li>
 *   <li>iBatis mapped-statement-id tags: a plain block comment placed
 *       immediately after the SQL verb, e.g.
 *       {@code SELECT /* TSKECD4C2_getTSKECD4C2ForSelResult_0001 *}{@code /}.</li>
 *   <li>Everything else: ordinary block comments and {@code -- } line
 *       comments (commonly used in this codebase's logs to annotate each
 *       VALUES entry with its column name).</li>
 * </ul>
 *
 * Comments are located with a single character-by-character scan that
 * tracks whether the cursor is inside a quoted string literal, so a
 * {@code --} or {@code /*} that only happens to appear inside a literal
 * value is never mistaken for a real comment.
 */
public class SqlCommentAnalyzer {

    private static final Pattern LEADING_VERB =
            Pattern.compile("^(SELECT|INSERT|UPDATE|DELETE|MERGE)\\b", Pattern.CASE_INSENSITIVE);

    public AnalyzedComments analyze(String sql) {
        List<CommentSpan> comments = new ArrayList<>();
        StringBuilder stripped = new StringBuilder(sql.length());

        int i = 0;
        int n = sql.length();
        boolean inString = false;

        while (i < n) {
            char c = sql.charAt(i);

            if (inString) {
                stripped.append(c);
                if (c == '\'') {
                    if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                        stripped.append('\'');
                        i += 2;
                        continue;
                    }
                    inString = false;
                }
                i++;
                continue;
            }

            if (c == '\'') {
                inString = true;
                stripped.append(c);
                i++;
                continue;
            }

            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                if (end == -1) {
                    end = n;
                }
                comments.add(new CommentSpan(i, end, CommentType.LINE, sql.substring(i, end)));
                appendBlanked(stripped, sql, i, end);
                i = end;
                continue;
            }

            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = (end == -1) ? n : end + 2;
                boolean isHint = i + 2 < n && sql.charAt(i + 2) == '+';
                comments.add(new CommentSpan(i, end, isHint ? CommentType.HINT : CommentType.BLOCK, sql.substring(i, end)));
                appendBlanked(stripped, sql, i, end);
                i = end;
                continue;
            }

            stripped.append(c);
            i++;
        }

        reclassifyStatementIdComment(sql, comments);

        return new AnalyzedComments(sql, stripped.toString(), comments);
    }

    /** Keeps newlines (for line-number stability) but blanks everything else in [start, end). */
    private void appendBlanked(StringBuilder out, String sql, int start, int end) {
        for (int k = start; k < end; k++) {
            out.append(sql.charAt(k) == '\n' ? '\n' : ' ');
        }
    }

    /**
     * A plain block comment sitting right after the leading SQL verb (with
     * only whitespace in between) is not a documentation comment - it is the
     * mapped-statement-id tag iBatis writes into the debug log.
     */
    private void reclassifyStatementIdComment(String sql, List<CommentSpan> comments) {
        String trimmed = sql.stripLeading();
        int leadOffset = sql.length() - trimmed.length();
        Matcher verb = LEADING_VERB.matcher(trimmed);
        if (!verb.find() || verb.start() != 0) {
            return;
        }
        int afterVerb = leadOffset + verb.end();

        for (CommentSpan span : comments) {
            if (span.type() != CommentType.BLOCK) {
                continue;
            }
            if (span.start() < afterVerb) {
                continue;
            }
            String between = sql.substring(afterVerb, span.start());
            if (between.isBlank()) {
                int idx = comments.indexOf(span);
                comments.set(idx, new CommentSpan(span.start(), span.end(), CommentType.STATEMENT_ID, span.text()));
            }
            break;
        }
    }
}
