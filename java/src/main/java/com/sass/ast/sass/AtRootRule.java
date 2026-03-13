package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An {@code @at-root} rule.
 */
public final class AtRootRule extends ParentStatement {

    private final @Nullable Interpolation query;
    private final FileSpan span;

    public AtRootRule(
            @Nullable Interpolation query,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(children, hasDeclarations);
        this.query = query;
        this.span = span;
    }

    public @Nullable Interpolation getQuery() {
        return query;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitAtRootRule(this);
    }
}
