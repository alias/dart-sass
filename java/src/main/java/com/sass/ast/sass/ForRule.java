package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * A {@code @for} rule.
 */
public final class ForRule extends ParentStatement {

    private final String variable;
    private final Expression from;
    private final Expression to;
    private final boolean isExclusive;
    private final FileSpan span;

    public ForRule(
            String variable,
            Expression from,
            Expression to,
            boolean isExclusive,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(children, hasDeclarations);
        this.variable = variable;
        this.from = from;
        this.to = to;
        this.isExclusive = isExclusive;
        this.span = span;
    }

    public String getVariable() {
        return variable;
    }

    public Expression getFrom() {
        return from;
    }

    public Expression getTo() {
        return to;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitForRule(this);
    }
}
