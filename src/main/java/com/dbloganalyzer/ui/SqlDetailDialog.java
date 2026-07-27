package com.dbloganalyzer.ui;

import com.dbloganalyzer.sql.CommentSpan;
import com.dbloganalyzer.sql.SqlRecord;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;

/** Shows one SQL statement's full text (comments/hints highlighted) plus its extracted column/value pairs. */
class SqlDetailDialog extends JDialog {

    private static final SimpleAttributeSet HINT_STYLE = colorStyle(new Color(0, 128, 0), true);
    private static final SimpleAttributeSet STATEMENT_ID_STYLE = colorStyle(new Color(120, 120, 120), true);
    private static final SimpleAttributeSet BLOCK_STYLE = colorStyle(new Color(150, 100, 40), false);
    private static final SimpleAttributeSet LINE_STYLE = colorStyle(new Color(150, 100, 40), false);

    SqlDetailDialog(Frame owner, SqlRecord record) {
        super(owner, "SQL 상세 - #" + record.sequence() + " " + record.type(), true);

        JTextPane textPane = new JTextPane();
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPane.setEditable(false);
        textPane.setText(record.rawSql());
        highlightComments(textPane, record);

        JTable columnValueTable = new JTable(new ColumnValueTableModel(record.columnValues()));
        columnValueTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JPanel infoPanel = new JPanel(new BorderLayout());
        String tag = record.statementIdTag();
        String info = "시각: " + record.timestamp() + "   스레드: " + record.thread()
                + (tag.isEmpty() ? "" : "   구문ID: " + tag)
                + (record.parsedOk() ? "" : "   [파싱 실패: " + record.parseError() + "]");
        infoPanel.add(new JLabel(info), BorderLayout.WEST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(textPane),
                new JScrollPane(columnValueTable));
        splitPane.setResizeWeight(0.65);

        setLayout(new BorderLayout());
        add(infoPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(900, 700));
        pack();
        setLocationRelativeTo(owner);
    }

    private void highlightComments(JTextPane textPane, SqlRecord record) {
        StyledDocument doc = textPane.getStyledDocument();
        for (CommentSpan comment : record.comments()) {
            SimpleAttributeSet style = switch (comment.type()) {
                case HINT -> HINT_STYLE;
                case STATEMENT_ID -> STATEMENT_ID_STYLE;
                case BLOCK -> BLOCK_STYLE;
                case LINE -> LINE_STYLE;
            };
            int length = comment.end() - comment.start();
            doc.setCharacterAttributes(comment.start(), length, style, false);
        }
    }

    private static SimpleAttributeSet colorStyle(Color color, boolean italic) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, color);
        StyleConstants.setItalic(set, italic);
        return set;
    }
}
