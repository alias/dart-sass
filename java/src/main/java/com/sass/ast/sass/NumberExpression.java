package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A number literal.
 */
public final class NumberExpression extends Expression {
    private final double value;
    private final @Nullable String unit;
    private final FileSpan span;

    public NumberExpression(double value, @Nullable String unit, FileSpan span) {
        this.value = value;
        this.unit = unit;
        this.span = span;
    }

    public NumberExpression(double value, FileSpan span) {
        this(value, null, span);
    }

    /** The numeric value of this expression. */
    public double getValue() {
        return value;
    }

    /** The unit of this number, or null if unitless. */
    public @Nullable String getUnit() {
        return unit;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitNumberExpression(this);
    }
}
