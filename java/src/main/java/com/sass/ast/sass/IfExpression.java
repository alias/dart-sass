package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * The legacy {@code if()} function expression.
 *
 * <p>This is treated specially because it short-circuits: only one of the
 * two branches is evaluated.
 */
public final class IfExpression extends Expression {
    private final ArgumentInvocation arguments;
    private final FileSpan span;

    public IfExpression(ArgumentInvocation arguments, FileSpan span) {
        this.arguments = arguments;
        this.span = span;
    }

    /** The arguments to this {@code if()} call. */
    public ArgumentInvocation getArguments() {
        return arguments;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitIfExpression(this);
    }
}
