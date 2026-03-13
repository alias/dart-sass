package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * An {@code @error} rule.
 */
public final class ErrorRule extends Statement {

    private final Expression expression;
    private final FileSpan span;

    public ErrorRule(Expression expression, FileSpan span) {
        this.expression = expression;
        this.span = span;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitErrorRule(this);
    }
}
