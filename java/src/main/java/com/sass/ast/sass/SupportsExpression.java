package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * An expression wrapping a {@link SupportsCondition}.
 *
 * <p>This is used to embed {@code @supports} conditions in expression contexts.
 */
public final class SupportsExpression extends Expression {
    private final SupportsCondition condition;

    public SupportsExpression(SupportsCondition condition) {
        this.condition = condition;
    }

    /** The supports condition. */
    public SupportsCondition getCondition() {
        return condition;
    }

    @Override
    public FileSpan getSpan() {
        return condition.getSpan();
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitSupportsExpression(this);
    }
}
