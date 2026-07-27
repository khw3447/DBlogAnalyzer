package com.dbloganalyzer.sql;

import java.util.List;

/**
 * Result of scanning a raw SQL string for comments.
 *
 * @param original the untouched SQL text
 * @param stripped the SQL text with every comment span blanked out (newlines
 *                 preserved) so it is safe to feed to a SQL grammar parser
 * @param comments every comment found, in source order
 */
public record AnalyzedComments(String original, String stripped, List<CommentSpan> comments) {
}
