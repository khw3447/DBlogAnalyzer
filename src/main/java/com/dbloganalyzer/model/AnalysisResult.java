package com.dbloganalyzer.model;

import com.dbloganalyzer.sql.SqlRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The full result of analyzing one log: every parsed SQL statement plus per-table rollups. */
public final class AnalysisResult {

    private final List<SqlRecord> records;
    private final Map<String, TableStat> tableStats;

    private AnalysisResult(List<SqlRecord> records, Map<String, TableStat> tableStats) {
        this.records = records;
        this.tableStats = tableStats;
    }

    public static AnalysisResult of(List<SqlRecord> records) {
        Map<String, TableStat> stats = new LinkedHashMap<>();
        for (SqlRecord record : records) {
            for (String table : record.tables()) {
                stats.computeIfAbsent(table, TableStat::new).increment(record.type());
            }
        }
        return new AnalysisResult(records, stats);
    }

    public List<SqlRecord> records() {
        return records;
    }

    public List<TableStat> tableStats() {
        return new ArrayList<>(tableStats.values());
    }

    public int parseFailureCount() {
        return (int) records.stream().filter(r -> !r.parsedOk()).count();
    }
}
