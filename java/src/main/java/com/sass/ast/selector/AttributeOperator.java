package com.sass.ast.selector;

/**
 * An operator used in a CSS attribute selector.
 */
public enum AttributeOperator {

    /** Exact match ({@code =}). */
    EQUAL("="),

    /** Word match ({@code ~=}). Matches if the value is a whitespace-separated list containing the given word. */
    INCLUDE("~="),

    /** Dash match ({@code |=}). Matches if the value is exactly the given value or starts with it followed by a hyphen. */
    DASH("|="),

    /** Prefix match ({@code ^=}). */
    PREFIX("^="),

    /** Suffix match ({@code $=}). */
    SUFFIX("$="),

    /** Substring match ({@code *=}). */
    SUBSTRING("*=");

    private final String operator;

    AttributeOperator(String operator) {
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
