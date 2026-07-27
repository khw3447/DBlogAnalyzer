package com.dbloganalyzer.ui;

import com.dbloganalyzer.sql.SqlRecord;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class SqlListTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"#", "시각", "타입", "테이블", "SQL 요약", "파싱"};

    private List<SqlRecord> rows = new ArrayList<>();

    void setRows(List<SqlRecord> records) {
        this.rows = new ArrayList<>(records);
        fireTableDataChanged();
    }

    SqlRecord recordAt(int row) {
        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SqlRecord record = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> record.sequence();
            case 1 -> record.timestamp();
            case 2 -> record.type();
            case 3 -> record.tables().stream().collect(Collectors.joining(", "));
            case 4 -> summarize(record.rawSql());
            case 5 -> record.parsedOk() ? "OK" : "실패";
            default -> "";
        };
    }

    private String summarize(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "..." : oneLine;
    }
}
