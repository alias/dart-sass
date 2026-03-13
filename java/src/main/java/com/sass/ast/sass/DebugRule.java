package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * A {@code @debug} rule.
 */
public final class DebugRule extends Statement {

    private final Expression expression;
    private final FileSpan span;

    public DebugRule(Expression expression, FileSpan span) {
        this.expression = expression;
        this.span = span;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitDebugRule(this);
    }
}
