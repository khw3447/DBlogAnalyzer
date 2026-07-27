package com.dbloganalyzer.sql;

import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

/** Renders a JSqlParser expression as the plain display text used throughout the UI. */
final class LiteralFormatter {

    private LiteralFormatter() {
    }

    static boolean isLiteral(Expression e) {
        return e instanceof StringValue || e instanceof LongValue || e instanceof DoubleValue || e instanceof NullValue;
    }

    static String text(Expression e) {
        if (e instanceof StringValue s) {
            return s.getValue();
        }
        if (e instanceof NullValue) {
            return "NULL";
        }
        if (e instanceof ExpressionList<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                Expression item = (Expression) o;
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(isLiteral(item) ? text(item) : item.toString());
            }
            return sb.toString();
        }
        return e.toString();
    }
}
