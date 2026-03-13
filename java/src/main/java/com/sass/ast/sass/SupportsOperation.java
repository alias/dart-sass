package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * An operation defining the relationship between two supports conditions.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/operation.dart}.
 */
public final class SupportsOperation implements SupportsCondition {

    /** The left-hand operand. */
    private final SupportsCondition left;

    /** The right-hand operand. */
    private final SupportsCondition right;

    /** The operator. */
    private final BooleanOperator operator;

    private final FileSpan span;

    public SupportsOperation(SupportsCondition left, SupportsCondition right,
                             BooleanOperator operator, FileSpan span) {
        this.left = left;
        this.right = right;
        this.operator = operator;
        this.span = span;
    }

    /** Returns the left-hand operand. */
    public SupportsCondition getLeft() {
        return left;
    }

    /** Returns the right-hand operand. */
    public SupportsCondition getRight() {
        return right;
    }

    /** Returns the operator. */
    public BooleanOperator getOperator() {
        return operator;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsOperation withSpan(FileSpan span) {
        return new SupportsOperation(left, right, operator, span);
    }

    @Override
    public String toString() {
        return parenthesize(left) + " " + operator + " " + parenthesize(right);
    }

    private String parenthesize(SupportsCondition condition) {
        if (condition instanceof SupportsNegation
                || (condition instanceof SupportsOperation op && op.operator == operator)) {
            return "(" + condition + ")";
        }
        return condition.toString();
    }
}
