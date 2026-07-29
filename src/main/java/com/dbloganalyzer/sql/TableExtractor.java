package com.dbloganalyzer.sql;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists every table (including those pulled in only through a JOIN) touched
 * by a statement. Names are uppercased before dedup: Oracle identifiers are
 * case-insensitive unless quoted, so the same log can spell a table as both
 * {@code inst1.TSKSAST04} and {@code INST1.TSKSAST04} - without normalizing,
 * those would be counted as two different tables instead of one.
 */
final class TableExtractor {

    private TableExtractor() {
    }

    static List<String> extract(Statement statement) {
        Set<String> tables = new LinkedHashSet<>();
        for (String name : new TablesNamesFinder().getTableList(statement)) {
            tables.add(name.toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(tables);
    }
}
