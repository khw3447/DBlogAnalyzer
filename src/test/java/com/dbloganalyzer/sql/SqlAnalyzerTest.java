package com.dbloganalyzer.sql;

import com.dbloganalyzer.log.LogParser;
import com.dbloganalyzer.log.RawSqlBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalyzerTest {

    private final SqlAnalyzer analyzer = new SqlAnalyzer();

    private List<RawSqlBlock> loadSampleBlocks() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample-log.txt")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new LogParser().extractSqlBlocks(text);
        }
    }

    @Test
    void selectStatementFindsOuterAndSubqueryTablesAndStatementIdTag() throws IOException {
        SqlRecord record = analyzer.analyze(loadSampleBlocks().get(0));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.SELECT, record.type());
        assertTrue(record.tables().contains("INST1.TSKECD4C2"));
        assertTrue(record.tables().contains("INST1.TSKECD4C4"));
        assertEquals("TSKECD4C2_getTSKECD4C2ForSelResult_0001", record.statementIdTag());
        assertTrue(record.comments().stream().anyMatch(c -> c.type() == CommentType.HINT));
        // WHERE conditions from both the outer query and the nested subquery must surface.
        assertTrue(record.columnValues().stream().anyMatch(cv -> cv.column().equals("배당지급구분") && cv.value().equals("2")));
        assertTrue(record.columnValues().stream().anyMatch(cv -> cv.column().equals("그룹회사코드") && cv.value().equals("K80")));
    }

    @Test
    void updateStatementExtractsSetAndWhereColumnValues() throws IOException {
        SqlRecord record = analyzer.analyze(loadSampleBlocks().get(1));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.UPDATE, record.type());
        assertEquals(List.of("INST1.TSKECOPKO"), record.tables());
        assertTrue(record.columnValues().stream()
                .anyMatch(cv -> cv.clause().equals("SET") && cv.column().equals("배도금액") && cv.value().equals("36780418")));
        assertTrue(record.columnValues().stream()
                .anyMatch(cv -> cv.clause().equals("WHERE") && cv.column().equals("운용기관관리번호") && cv.value().equals("24442594")));
    }

    @Test
    void insertStatementPairsColumnsWithValuesIgnoringTrailingLineComments() throws IOException {
        SqlRecord record = analyzer.analyze(loadSampleBlocks().get(2));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.INSERT, record.type());
        assertEquals(List.of("INST1.TSKECPK32"), record.tables());
        assertTrue(record.columnValues().stream()
                .anyMatch(cv -> cv.column().equals("자산관리기관코드") && cv.value().equals("B004")));
        assertTrue(record.comments().stream().filter(c -> c.type() == CommentType.LINE).count() >= 5);
    }

    @Test
    void deleteStatementDetected() throws IOException {
        SqlRecord record = analyzer.analyze(loadSampleBlocks().get(3));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.DELETE, record.type());
        assertEquals(List.of("INST1.TSKECD4C2"), record.tables());
    }

    @Test
    void mergeStatementDetectedWithAllTables() throws IOException {
        SqlRecord record = analyzer.analyze(loadSampleBlocks().get(4));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.MERGE, record.type());
        assertTrue(record.tables().contains("INST1.TSKECOPKO"));
    }
}
