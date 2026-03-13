package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * A {@code @media} rule.
 */
public final class MediaRule extends ParentStatement {

    private final Interpolation query;
    private final FileSpan span;

    public MediaRule(Interpolation query, List<Statement> children,
                     boolean hasDeclarations, FileSpan span) {
        super(children, hasDeclarations);
        this.query = query;
        this.span = span;
    }

    public Interpolation getQuery() {
        return query;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitMediaRule(this);
    }
}
