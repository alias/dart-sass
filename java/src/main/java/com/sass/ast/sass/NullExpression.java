package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A null literal.
 */
public final class NullExpression extends Expression {
    private final FileSpan span;

    public NullExpression(FileSpan span) {
        this.span = span;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitNullExpression(this);
    }
}
