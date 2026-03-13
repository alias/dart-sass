package com.sass.ast.sass;

/**
 * A unary operator used in a Sass expression.
 */
public enum UnaryOperator {
    PLUS("plus", "+"),
    MINUS("minus", "-"),
    DIVIDE("divide", "/"),
    NOT("not", "not");

    private final String name;
    private final String operator;

    UnaryOperator(String name, String operator) {
        this.name = name;
        this.operator = operator;
    }

    /** The human-readable name of this operator. */
    public String getName() {
        return name;
    }

    /** The operator string as it appears in Sass source. */
    public String getOperator() {
        return operator;
    }

    @Override
    public String toString() {
        return operator;
    }
}
