package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * A {@code @supports} rule.
 */
public final class SupportsRule extends ParentStatement {

    private final SupportsCondition condition;
    private final FileSpan span;

    public SupportsRule(SupportsCondition condition, List<Statement> children,
                        boolean hasDeclarations, FileSpan span) {
        super(children, hasDeclarations);
        this.condition = condition;
        this.span = span;
    }

    public SupportsCondition getCondition() {
        return condition;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitSupportsRule(this);
    }
}
