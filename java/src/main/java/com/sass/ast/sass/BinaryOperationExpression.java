package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A binary operation expression (e.g., {@code $a + $b}).
 */
public final class BinaryOperationExpression extends Expression {
    private final BinaryOperator operator;
    private final Expression left;
    private final Expression right;
    private final boolean allowsSlash;
    private final FileSpan span;

    public BinaryOperationExpression(BinaryOperator operator, Expression left,
                                     Expression right, boolean allowsSlash, FileSpan span) {
        this.operator = operator;
        this.left = left;
        this.right = right;
        this.allowsSlash = allowsSlash;
        this.span = span;
    }

    public BinaryOperationExpression(BinaryOperator operator, Expression left,
                                     Expression right, FileSpan span) {
        this(operator, left, right, false, span);
    }

    /** The operator. */
    public BinaryOperator getOperator() {
        return operator;
    }

    /** The left-hand operand. */
    public Expression getLeft() {
        return left;
    }

    /** The right-hand operand. */
    public Expression getRight() {
        return right;
    }

    /**
     * Whether this operation is allowed to be interpreted as a slash-separated
     * value (e.g., {@code 1/2} in a font shorthand).
     */
    public boolean allowsSlash() {
        return allowsSlash;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitBinaryOperationExpression(this);
    }
}
