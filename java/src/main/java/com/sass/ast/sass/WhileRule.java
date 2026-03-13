package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * A {@code @while} rule.
 */
public final class WhileRule extends ParentStatement {

    private final Expression condition;
    private final FileSpan span;

    public WhileRule(Expression condition, List<Statement> children,
                     boolean hasDeclarations, FileSpan span) {
        super(children, hasDeclarations);
        this.condition = condition;
        this.span = span;
    }

    public Expression getCondition() {
        return condition;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitWhileRule(this);
    }
}
