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
        try (InputStream in = getClass().getResourceAsStream("/sample-log.txt")) {
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
}
