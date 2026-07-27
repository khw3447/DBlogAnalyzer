package com.dbloganalyzer.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlCommentAnalyzerTest {

    private final SqlCommentAnalyzer analyzer = new SqlCommentAnalyzer();

    @Test
    void classifiesLeadingCommentAsStatementId() {
        String sql = "SELECT /* TSKECD4C2_getTSKECD4C2ForSelResult_0001 */ A.COL FROM A";

        List<CommentSpan> comments = analyzer.analyze(sql).comments();

        assertEquals(1, comments.size());
        assertEquals(CommentType.STATEMENT_ID, comments.get(0).type());
    }

    @Test
    void classifiesPlusPrefixedCommentAsHint() {
        String sql = "SELECT /*+ INDEX(A IDX_A) */ A.COL FROM A";

        List<CommentSpan> comments = analyzer.analyze(sql).comments();

        assertEquals(1, comments.size());
        assertEquals(CommentType.HINT, comments.get(0).type());
    }

    @Test
    void classifiesLineCommentAfterAValueAsLine() {
        String sql = "INSERT INTO A (COL) VALUES ('K80' -- COL\n)";

        List<CommentSpan> comments = analyzer.analyze(sql).comments();

        assertEquals(1, comments.size());
        assertEquals(CommentType.LINE, comments.get(0).type());
    }

    @Test
    void doesNotTreatDashesInsideStringLiteralAsComment() {
        String sql = "SELECT * FROM A WHERE COL = '2026-07-24--not-a-comment'";

        List<CommentSpan> comments = analyzer.analyze(sql).comments();

        assertTrue(comments.isEmpty());
        assertTrue(analyzer.analyze(sql).stripped().contains("2026-07-24--not-a-comment"));
    }

    @Test
    void strippedTextRemovesCommentsButKeepsSqlParsable() {
        String sql = "SELECT /* stmt-id */ /*+ INDEX(A IDX) */ A.COL -- trailing note\nFROM A";

        String stripped = analyzer.analyze(sql).stripped();

        assertTrue(stripped.contains("SELECT"));
        assertTrue(stripped.contains("A.COL"));
        assertTrue(!stripped.contains("stmt-id"));
        assertTrue(!stripped.contains("INDEX"));
        assertTrue(!stripped.contains("trailing note"));
    }
}
