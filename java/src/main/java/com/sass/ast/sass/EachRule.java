package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * An {@code @each} rule.
 */
public final class EachRule extends ParentStatement {

    private final List<String> variables;
    private final Expression list;
    private final FileSpan span;

    public EachRule(
            List<String> variables,
            Expression list,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(children, hasDeclarations);
        this.variables = List.copyOf(variables);
        this.list = list;
        this.span = span;
    }

    public List<String> getVariables() {
        return variables;
    }

    public Expression getList() {
        return list;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitEachRule(this);
    }
}
