package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.value.SassColor;
import com.sass.visitor.ExpressionVisitor;

/**
 * A color literal.
 */
public final class ColorExpression extends Expression {
    private final SassColor value;
    private final FileSpan span;

    public ColorExpression(SassColor value, FileSpan span) {
        this.value = value;
        this.span = span;
    }

    /** The color value of this expression. */
    public SassColor getValue() {
        return value;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitColorExpression(this);
    }
}
