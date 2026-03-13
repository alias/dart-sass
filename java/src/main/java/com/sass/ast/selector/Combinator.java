package com.sass.ast.selector;

/**
 * A combinator that defines the relationship between compound selectors in a
 * complex selector.
 */
public enum Combinator {

    /** The child combinator ({@code >}). */
    CHILD(">"),

    /** The next-sibling combinator ({@code +}). */
    NEXT_SIBLING("+"),

    /** The following-sibling combinator ({@code ~}). */
    FOLLOWING_SIBLING("~");

    private final String operator;

    Combinator(String operator) {
        this.operator = operator;
    }

    /** Returns the operator string as it appears in CSS source. */
    public String getOperator() {
        return operator;
    }

    @Override
    public String toString() {
        return operator;
    }
}
