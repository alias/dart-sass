package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * A {@code @warn} rule.
 */
public final class WarnRule extends Statement {

    private final Expression expression;
    private final FileSpan span;

    public WarnRule(Expression expression, FileSpan span) {
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
        return visitor.visitWarnRule(this);
    }
}
