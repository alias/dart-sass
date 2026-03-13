package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A parent selector reference ({@code &}).
 */
public final class SelectorExpression extends Expression {
    private final FileSpan span;

    public SelectorExpression(FileSpan span) {
        this.span = span;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitSelectorExpression(this);
    }
}
