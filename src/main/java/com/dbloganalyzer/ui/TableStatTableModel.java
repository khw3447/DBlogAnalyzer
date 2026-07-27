package com.dbloganalyzer.ui;

import com.dbloganalyzer.model.TableStat;
import com.dbloganalyzer.sql.SqlType;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class TableStatTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"테이블", "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "합계"};
    private static final SqlType[] TYPES = {SqlType.SELECT, SqlType.INSERT, SqlType.UPDATE, SqlType.DELETE, SqlType.MERGE};

    private List<TableStat> rows = new ArrayList<>();

    void setRows(List<TableStat> stats) {
        this.rows = new ArrayList<>(stats);
        this.rows.sort(Comparator.comparing(TableStat::total).reversed());
        fireTableDataChanged();
    }

    String tableAt(int row) {
        return rows.get(row).table();
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
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? String.class : Integer.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TableStat stat = rows.get(rowIndex);
        if (columnIndex == 0) {
            return stat.table();
        }
        if (columnIndex == COLUMNS.length - 1) {
            return stat.total();
        }
        return stat.count(TYPES[columnIndex - 1]);
    }
}
