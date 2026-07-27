package com.dbloganalyzer.ui;

import com.dbloganalyzer.sql.ColumnValue;

import javax.swing.table.AbstractTableModel;
import java.util.List;

class ColumnValueTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"절", "테이블/별칭", "컬럼", "연산자", "값"};

    private final List<ColumnValue> rows;

    ColumnValueTableModel(List<ColumnValue> rows) {
        this.rows = rows;
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
        ColumnValue cv = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> cv.clause();
            case 1 -> cv.table();
            case 2 -> cv.column();
            case 3 -> cv.operator();
            case 4 -> cv.value();
            default -> "";
        };
    }
}
