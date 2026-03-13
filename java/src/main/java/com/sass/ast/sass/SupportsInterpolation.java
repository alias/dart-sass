package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * An interpolated supports condition.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/interpolation.dart}.
 */
public final class SupportsInterpolation implements SupportsCondition {

    /** The expression in the interpolation. */
    private final Expression expression;

    private final FileSpan span;

    public SupportsInterpolation(Expression expression, FileSpan span) {
        this.expression = expression;
        this.span = span;
    }

    /** Returns the expression in the interpolation. */
    public Expression getExpression() {
        return expression;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsInterpolation withSpan(FileSpan span) {
        return new SupportsInterpolation(expression, span);
    }

    @Override
    public String toString() {
        return "#{" + expression + "}";
    }
}
