package com.dbloganalyzer.log;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {

    private String loadSampleLog() throws IOException {
        return loadResource("/sample-log.txt");
    }

    private String loadBracketSampleLog() throws IOException {
        return loadResource("/sample-log-bracket.txt");
    }

    private String loadResource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsAllFiveSqlBlocksFromSampleLog() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadSampleLog());

        assertEquals(5, blocks.size());
        assertTrue(blocks.get(0).rawSql().startsWith("SELECT"));
        assertTrue(blocks.get(1).rawSql().startsWith("UPDATE"));
        assertTrue(blocks.get(2).rawSql().startsWith("INSERT"));
        assertTrue(blocks.get(3).rawSql().startsWith("DELETE"));
        assertTrue(blocks.get(4).rawSql().startsWith("MERGE"));
    }

    @Test
    void stopsCapturingAtNextLogPrefixedLine() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadSampleLog());

        // The UPDATE block must not swallow the "iBatis ConfigurationLoader" line
        // that appears (as its own log entry) between the SELECT and UPDATE blocks.
        assertTrue(blocks.get(0).rawSql().contains("ORDER BY"));
        assertTrue(!blocks.get(1).rawSql().contains("iBatis ConfigurationLoader"));
    }

    @Test
    void capturesTimestampAndThread() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadSampleLog());

        assertEquals("2026-07-24 10:56:08,061", blocks.get(0).timestamp());
        assertEquals("mkec495b-1", blocks.get(0).thread());
    }

    @Test
    void extractsSqlBlocksFromBracketedLogFormat() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadBracketSampleLog());

        assertEquals(3, blocks.size());
        assertTrue(blocks.get(0).rawSql().startsWith("SELECT"));
        assertTrue(blocks.get(1).rawSql().startsWith("UPDATE"));
        assertTrue(blocks.get(2).rawSql().startsWith("insert"));
    }

    @Test
    void bracketedFormatCapturesTimestampAndProgramCodeAsThread() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadBracketSampleLog());

        assertEquals("2026-07-23 18:03:57", blocks.get(0).timestamp());
        assertEquals("KEC0649142", blocks.get(0).thread());
    }

    @Test
    void bracketedFormatDoesNotSwallowNonSqlLinesBetweenBlocks() throws IOException {
        List<RawSqlBlock> blocks = new LogParser().extractSqlBlocks(loadBracketSampleLog());

        assertTrue(!blocks.get(1).rawSql().contains("TransactionCoordinator"));
        assertTrue(!blocks.get(2).rawSql().contains("TheK_ERROR_FORMAT"));
    }
}
