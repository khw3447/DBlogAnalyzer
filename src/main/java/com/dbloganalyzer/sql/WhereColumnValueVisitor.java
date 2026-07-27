package com.dbloganalyzer.sql;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a WHERE/ON/andPredicate expression tree and collects every
 * "column operator literal" comparison it finds, regardless of how deeply
 * it is nested inside AND/OR/parenthesis groups (the default
 * {@link ExpressionVisitorAdapter} traversal handles descending into those
 * automatically).
 */
class WhereColumnValueVisitor extends ExpressionVisitorAdapter {

    private final String clause;
    private final List<ColumnValue> results = new ArrayList<>();

    WhereColumnValueVisitor(String clause) {
        this.clause = clause;
    }

    List<ColumnValue> results() {
        return results;
    }

    @Override
    public void visit(EqualsTo expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(NotEqualsTo expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(GreaterThan expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(GreaterThanEquals expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(MinorThan expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(MinorThanEquals expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(LikeExpression expr) {
        handleBinary(expr);
    }

    @Override
    public void visit(InExpression expr) {
        Expression left = expr.getLeftExpression();
        if (left instanceof Column column) {
            String value = LiteralFormatter.text(expr.getRightExpression());
            results.add(toColumnValue(column, expr.isNot() ? "NOT IN" : "IN", value));
        }
        left.accept(this);
    }

    private void handleBinary(BinaryExpression expr) {
        Expression left = expr.getLeftExpression();
        Expression right = expr.getRightExpression();
        if (left instanceof Column column && LiteralFormatter.isLiteral(right)) {
            results.add(toColumnValue(column, expr.getStringExpression(), LiteralFormatter.text(right)));
        } else if (right instanceof Column column && LiteralFormatter.isLiteral(left)) {
            results.add(toColumnValue(column, expr.getStringExpression(), LiteralFormatter.text(left)));
        }
        left.accept(this);
        right.accept(this);
    }

    private ColumnValue toColumnValue(Column column, String operator, String value) {
        String table = column.getTable() != null && column.getTable().getName() != null
                ? column.getTable().getName()
                : "";
        return new ColumnValue(clause, table, column.getColumnName(), operator, value);
    }
}
