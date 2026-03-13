package com.sass.ast.sass;

/**
 * A binary operator used in a Sass expression.
 */
public enum BinaryOperator {
    SINGLE_EQUALS("single equals", "=", 0),
    OR("or", "or", 1),
    AND("and", "and", 2),
    EQUALS("equals", "==", 3),
    NOT_EQUALS("not equals", "!=", 3),
    GREATER_THAN("greater than", ">", 4),
    GREATER_THAN_OR_EQUALS("greater than or equals", ">=", 4),
    LESS_THAN("less than", "<", 4),
    LESS_THAN_OR_EQUALS("less than or equals", "<=", 4),
    PLUS("plus", "+", 5),
    MINUS("minus", "-", 5),
    TIMES("times", "*", 6),
    DIVIDED_BY("divided by", "/", 6),
    MODULO("modulo", "%", 6);

    private final String name;
    private final String operator;
    private final int precedence;

    BinaryOperator(String name, String operator, int precedence) {
        this.name = name;
        this.operator = operator;
        this.precedence = precedence;
    }

    /** The human-readable name of this operator. */
    public String getName() {
        return name;
    }

    /** The operator string as it appears in Sass source. */
    public String getOperator() {
        return operator;
    }

    /** The precedence of this operator, with higher values binding tighter. */
    public int getPrecedence() {
        return precedence;
    }

    @Override
    public String toString() {
        return operator;
    }
}
