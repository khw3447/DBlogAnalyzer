package com.dbloganalyzer.ui;

import com.dbloganalyzer.log.LogParser;
import com.dbloganalyzer.log.RawSqlBlock;
import com.dbloganalyzer.model.AnalysisResult;
import com.dbloganalyzer.sql.SqlAnalyzer;
import com.dbloganalyzer.sql.SqlRecord;
import com.dbloganalyzer.sql.SqlType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MouseInputAdapter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {

    private final LogParser logParser = new LogParser();
    private final SqlAnalyzer sqlAnalyzer = new SqlAnalyzer();

    private final JTextArea logInput = new JTextArea();
    private final TableStatTableModel tableStatModel = new TableStatTableModel();
    private final SqlListTableModel sqlListModel = new SqlListTableModel();
    private final JTable tableStatsView = new JTable(tableStatModel);
    private final JTable sqlListView = new JTable(sqlListModel);
    private final JLabel statusLabel = new JLabel("로그를 불러오거나 붙여넣은 뒤 [분석 실행]을 누르세요.");
    private final Map<SqlType, JCheckBox> typeFilters = new EnumMap<>(SqlType.class);
    private final JTextField searchField = new JTextField(20);

    private AnalysisResult currentResult;
    private String selectedTable;

    public MainFrame() {
        super("DBLogAnalyzer - iBatis/Oracle SQL 로그 분석기");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildInputPanel(), BorderLayout.NORTH);
        add(buildResultPanel(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private JPanel buildInputPanel() {
        logInput.setLineWrap(false);
        logInput.setRows(8);
        JScrollPane inputScroll = new JScrollPane(logInput);
        inputScroll.setBorder(BorderFactory.createTitledBorder("로그 붙여넣기"));

        JButton openFileButton = new JButton("파일 열기...");
        openFileButton.addActionListener(e -> openFile());

        JButton analyzeButton = new JButton("분석 실행");
        analyzeButton.addActionListener(e -> runAnalysis());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(openFileButton);
        buttonPanel.add(analyzeButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(inputScroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(1200, 220));
        return panel;
    }

    private JPanel buildResultPanel() {
        tableStatsView.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableStatsView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableStatsView.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onTableSelectionChanged();
            }
        });
        JScrollPane tableStatsScroll = new JScrollPane(tableStatsView);
        tableStatsScroll.setBorder(BorderFactory.createTitledBorder("테이블별 SQL 사용 현황 (행 클릭 시 필터)"));

        sqlListView.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        sqlListView.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedDetail();
                }
            }
        });
        JScrollPane sqlListScroll = new JScrollPane(sqlListView);

        JPanel filterAndSearch = new JPanel();
        filterAndSearch.setLayout(new javax.swing.BoxLayout(filterAndSearch, javax.swing.BoxLayout.Y_AXIS));
        filterAndSearch.add(buildFilterPanel());
        filterAndSearch.add(buildSearchPanel());

        JPanel sqlListPanel = new JPanel(new BorderLayout());
        sqlListPanel.setBorder(BorderFactory.createTitledBorder("SQL 목록 (더블클릭 시 상세보기)"));
        sqlListPanel.add(filterAndSearch, BorderLayout.NORTH);
        sqlListPanel.add(sqlListScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableStatsScroll, sqlListPanel);
        splitPane.setResizeWeight(0.35);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("SQL 종류:"));
        for (SqlType type : SqlType.values()) {
            JCheckBox checkBox = new JCheckBox(type.name(), true);
            checkBox.addActionListener(e -> refreshSqlList());
            typeFilters.put(type, checkBox);
            panel.add(checkBox);
        }
        JButton clearTableFilter = new JButton("테이블 필터 해제");
        clearTableFilter.addActionListener(e -> {
            selectedTable = null;
            tableStatsView.clearSelection();
            refreshSqlList();
        });
        panel.add(clearTableFilter);
        return panel;
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("컬럼/값 검색:"));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshSqlList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSqlList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSqlList();
            }
        });
        panel.add(searchField);
        JButton clearSearch = new JButton("지우기");
        clearSearch.addActionListener(e -> searchField.setText(""));
        panel.add(clearSearch);
        return panel;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            try {
                String content = Files.readString(path, detectCharset(path));
                logInput.setText(content);
                statusLabel.setText("파일을 불러왔습니다: " + path.getFileName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "파일을 읽을 수 없습니다: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Charset detectCharset(Path path) {
        // Legacy Korean batch logs are frequently written in EUC-KR; fall back to it
        // only if the file cannot be decoded as UTF-8 without replacement characters.
        try {
            byte[] bytes = Files.readAllBytes(path);
            String asUtf8 = new String(bytes, StandardCharsets.UTF_8);
            if (!asUtf8.contains("�")) {
                return StandardCharsets.UTF_8;
            }
        } catch (IOException ignored) {
            // fall through to default below
        }
        return Charset.forName("EUC-KR");
    }

    private void runAnalysis() {
        String text = logInput.getText();
        if (text.isBlank()) {
            JOptionPane.showMessageDialog(this, "분석할 로그 내용이 없습니다.", "안내", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<RawSqlBlock> blocks = logParser.extractSqlBlocks(text);
        List<SqlRecord> records = blocks.stream().map(sqlAnalyzer::analyze).collect(Collectors.toList());
        currentResult = AnalysisResult.of(records);
        selectedTable = null;
        tableStatsView.clearSelection();

        tableStatModel.setRows(currentResult.tableStats());
        refreshSqlList();

        statusLabel.setText(String.format("SQL %d건 분석 완료 (테이블 %d개, 파싱 실패 %d건)",
                records.size(), currentResult.tableStats().size(), currentResult.parseFailureCount()));
    }

    private void onTableSelectionChanged() {
        int row = tableStatsView.getSelectedRow();
        selectedTable = row >= 0 ? tableStatModel.tableAt(tableStatsView.convertRowIndexToModel(row)) : null;
        refreshSqlList();
    }

    private void refreshSqlList() {
        if (currentResult == null) {
            return;
        }
        Set<SqlType> enabledTypes = typeFilters.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SqlType.class)));

        String searchTerm = searchField.getText().strip().toLowerCase();

        List<SqlRecord> filtered = currentResult.records().stream()
                .filter(r -> enabledTypes.contains(r.type()))
                .filter(r -> selectedTable == null || r.tables().contains(selectedTable))
                .filter(r -> searchTerm.isEmpty() || r.columnValues().stream().anyMatch(cv ->
                        cv.column().toLowerCase().contains(searchTerm) || cv.value().toLowerCase().contains(searchTerm)))
                .collect(Collectors.toList());

        sqlListModel.setRows(filtered);
    }

    private void openSelectedDetail() {
        int row = sqlListView.getSelectedRow();
        if (row < 0) {
            return;
        }
        SqlRecord record = sqlListModel.recordAt(sqlListView.convertRowIndexToModel(row));
        new SqlDetailDialog(this, record).setVisible(true);
    }
}
