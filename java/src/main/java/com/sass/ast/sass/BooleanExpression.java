package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A boolean literal.
 */
public final class BooleanExpression extends Expression {
    private final boolean value;
    private final FileSpan span;

    public BooleanExpression(boolean value, FileSpan span) {
        this.value = value;
        this.span = span;
    }

    /** The value of this expression. */
    public boolean getValue() {
        return value;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitBooleanExpression(this);
    }
}
