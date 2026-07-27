package com.dbloganalyzer.model;

import com.dbloganalyzer.sql.SqlType;

import java.util.EnumMap;
import java.util.Map;

/** Per-table usage counts, one bucket per SQL command type. */
public final class TableStat {

    private final String table;
    private final Map<SqlType, Integer> counts = new EnumMap<>(SqlType.class);

    public TableStat(String table) {
        this.table = table;
    }

    public String table() {
        return table;
    }

    void increment(SqlType type) {
        counts.merge(type, 1, Integer::sum);
    }

    public int count(SqlType type) {
        return counts.getOrDefault(type, 0);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
