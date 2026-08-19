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
        return loadBlocks("/sample-log.txt");
    }

    private List<RawSqlBlock> loadBracketSampleBlocks() throws IOException {
        return loadBlocks("/sample-log-bracket.txt");
    }

    private List<RawSqlBlock> loadBlocks(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
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

    @Test
    void bracketedFormatSelectParsesCorrectly() throws IOException {
        SqlRecord record = analyzer.analyze(loadBracketSampleBlocks().get(0));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.SELECT, record.type());
        assertEquals(List.of("INST1.TSKECC101"), record.tables());
        assertEquals("KEC0649142", record.thread());
        assertEquals("KB0502310200120260723180357000093002", record.guid());
        assertTrue(record.columnValues().stream().anyMatch(cv -> cv.column().equals("그룹회사코드") && cv.value().equals("KB0")));
    }

    @Test
    void bracketedFormatUpdateParsesCorrectly() throws IOException {
        SqlRecord record = analyzer.analyze(loadBracketSampleBlocks().get(1));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.UPDATE, record.type());
        assertEquals(List.of("INST1.TSKECC201"), record.tables());
        assertTrue(record.columnValues().stream()
                .anyMatch(cv -> cv.clause().equals("SET") && cv.column().equals("퇴직연금거래결과구분") && cv.value().equals("B")));
    }

    @Test
    void bracketedFormatLowercaseInsertWithMultilineXmlLiteralParsesCorrectly() throws IOException {
        SqlRecord record = analyzer.analyze(loadBracketSampleBlocks().get(2));

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(SqlType.INSERT, record.type());
        assertEquals(List.of("INST1.TSKSAST04"), record.tables());
        assertTrue(record.columnValues().stream()
                .anyMatch(cv -> cv.column().equals("시스템경로번호") && cv.value().equals("002")));
    }

    @Test
    void whereClauseFunctionCallValueIsCaptured() {
        RawSqlBlock block = new RawSqlBlock(1, "2026-01-01 00:00:00,000", "t", "",
                "SELECT 1 FROM DUAL WHERE 가입년월일 <= TO_CHAR(SYSDATE, 'YYYYMMDD') "
                        + "AND 해제년월일 = NVL(종료일자, '99991231')");

        SqlRecord record = analyzer.analyze(block);

        assertTrue(record.parsedOk(), record.parseError());
        assertTrue(record.columnValues().stream().anyMatch(cv ->
                cv.column().equals("가입년월일") && cv.value().contains("TO_CHAR") && cv.value().contains("SYSDATE")));
        assertTrue(record.columnValues().stream().anyMatch(cv ->
                cv.column().equals("해제년월일") && cv.value().contains("NVL")));
    }

    @Test
    void tableNamesAreNormalizedToUppercaseSoCaseVariantsCollapse() {
        RawSqlBlock block = new RawSqlBlock(1, "2026-01-01 00:00:00,000", "t", "",
                "SELECT a.col FROM Inst1.TskeCd4c2 a, INST1.TSKECD4C2 b WHERE a.col = b.col");

        SqlRecord record = analyzer.analyze(block);

        assertTrue(record.parsedOk(), record.parseError());
        assertEquals(List.of("INST1.TSKECD4C2"), record.tables());
    }
}
