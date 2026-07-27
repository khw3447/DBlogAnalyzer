package com.dbloganalyzer.sql;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Lists every table (including those pulled in only through a JOIN) touched by a statement. */
final class TableExtractor {

    private TableExtractor() {
    }

    static List<String> extract(Statement statement) {
        Set<String> tables = new LinkedHashSet<>(new TablesNamesFinder().getTableList(statement));
        return new ArrayList<>(tables);
    }
}
