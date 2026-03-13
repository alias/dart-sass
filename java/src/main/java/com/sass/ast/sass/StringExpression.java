package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A string literal, which may contain interpolations.
 */
public final class StringExpression extends Expression {
    private final Interpolation text;
    private final boolean hasQuotes;

    public StringExpression(Interpolation text, boolean hasQuotes) {
        this.text = text;
        this.hasQuotes = hasQuotes;
    }

    public StringExpression(Interpolation text) {
        this(text, false);
    }

    /** The interpolated text of this string. */
    public Interpolation getText() {
        return text;
    }

    /** Whether this string is quoted. */
    public boolean hasQuotes() {
        return hasQuotes;
    }

    @Override
    public FileSpan getSpan() {
        return text.getSpan();
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitStringExpression(this);
    }
}
