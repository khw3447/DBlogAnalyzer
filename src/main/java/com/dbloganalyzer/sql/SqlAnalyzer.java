package com.dbloganalyzer.sql;

import com.dbloganalyzer.log.RawSqlBlock;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw "SQL:" log block into a fully analyzed {@link SqlRecord}:
 * classifies its comments, determines the statement type, and - when the
 * text parses as valid SQL - lists the tables and column/value pairs it
 * touches. A statement that JSqlParser cannot parse is still reported (type
 * detected by keyword, no table/column detail) rather than dropped, since a
 * single unusual statement should not hide the rest of the analysis.
 */
public class SqlAnalyzer {

    private static final Pattern LEADING_VERB =
            Pattern.compile("^(SELECT|INSERT|UPDATE|DELETE|MERGE)\\b", Pattern.CASE_INSENSITIVE);

    private final SqlCommentAnalyzer commentAnalyzer = new SqlCommentAnalyzer();

    public SqlRecord analyze(RawSqlBlock block) {
        AnalyzedComments analyzed = commentAnalyzer.analyze(block.rawSql());
        SqlType keywordType = detectTypeByKeyword(analyzed.stripped());

        try {
            Statement statement = CCJSqlParserUtil.parse(analyzed.stripped());
            SqlType type = typeOf(statement, keywordType);
            List<String> tables = TableExtractor.extract(statement);
            List<ColumnValue> columnValues = ColumnValueExtractor.extract(statement);
            return new SqlRecord(block.sequence(), block.timestamp(), block.thread(), type, tables,
                    columnValues, analyzed.comments(), block.rawSql(), true, null);
        } catch (JSQLParserException | RuntimeException e) {
            return new SqlRecord(block.sequence(), block.timestamp(), block.thread(), keywordType, List.of(),
                    List.of(), analyzed.comments(), block.rawSql(), false, rootMessage(e));
        }
    }

    private SqlType typeOf(Statement statement, SqlType fallback) {
        if (statement instanceof Select) return SqlType.SELECT;
        if (statement instanceof Insert) return SqlType.INSERT;
        if (statement instanceof Update) return SqlType.UPDATE;
        if (statement instanceof Delete) return SqlType.DELETE;
        if (statement instanceof Merge) return SqlType.MERGE;
        return fallback;
    }

    private SqlType detectTypeByKeyword(String sql) {
        Matcher m = LEADING_VERB.matcher(sql.stripLeading());
        if (!m.find() || m.start() != 0) {
            return SqlType.OTHER;
        }
        return switch (m.group(1).toUpperCase()) {
            case "SELECT" -> SqlType.SELECT;
            case "INSERT" -> SqlType.INSERT;
            case "UPDATE" -> SqlType.UPDATE;
            case "DELETE" -> SqlType.DELETE;
            case "MERGE" -> SqlType.MERGE;
            default -> SqlType.OTHER;
        };
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
