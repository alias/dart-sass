package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A function invocation expression where the function name is an interpolation.
 *
 * <p>This is used for function calls like {@code foo-#{$bar}(...)}.
 */
public final class InterpolatedFunctionExpression extends Expression {
    private final Interpolation name;
    private final ArgumentInvocation arguments;
    private final FileSpan span;

    public InterpolatedFunctionExpression(Interpolation name, ArgumentInvocation arguments,
                                          FileSpan span) {
        this.name = name;
        this.arguments = arguments;
        this.span = span;
    }

    /** The interpolated name of this function. */
    public Interpolation getName() {
        return name;
    }

    /** The arguments to this function. */
    public ArgumentInvocation getArguments() {
        return arguments;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitInterpolatedFunctionExpression(this);
    }
}
