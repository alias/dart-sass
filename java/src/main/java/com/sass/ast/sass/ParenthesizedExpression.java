package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A parenthesized expression.
 */
public final class ParenthesizedExpression extends Expression {
    private final Expression expression;
    private final FileSpan span;

    public ParenthesizedExpression(Expression expression, FileSpan span) {
        this.expression = expression;
        this.span = span;
    }

    /** The inner expression. */
    public Expression getExpression() {
        return expression;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitParenthesizedExpression(this);
    }
}
