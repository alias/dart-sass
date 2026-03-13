package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A unary operation expression (e.g., {@code -$a} or {@code not $b}).
 */
public final class UnaryOperationExpression extends Expression {
    private final UnaryOperator operator;
    private final Expression operand;
    private final FileSpan span;

    public UnaryOperationExpression(UnaryOperator operator, Expression operand, FileSpan span) {
        this.operator = operator;
        this.operand = operand;
        this.span = span;
    }

    /** The operator. */
    public UnaryOperator getOperator() {
        return operator;
    }

    /** The operand. */
    public Expression getOperand() {
        return operand;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitUnaryOperationExpression(this);
    }
}
