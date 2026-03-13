package com.sass.ast.sass;

/**
 * An enum for binary boolean operations used in {@code @supports} conditions.
 *
 * <p>Currently CSS only supports conjunctions ({@code and}) and disjunctions ({@code or}).
 *
 * <p>Port of {@code lib/src/ast/sass/boolean_operator.dart}.
 */
public enum BooleanOperator {
    AND("and"),
    OR("or");

    private final String cssName;

    BooleanOperator(String cssName) {
        this.cssName = cssName;
    }

    /** Returns the CSS name of this operator (e.g. "and" or "or"). */
    public String getCssName() {
        return cssName;
    }

    @Override
    public String toString() {
        return cssName;
    }
}
