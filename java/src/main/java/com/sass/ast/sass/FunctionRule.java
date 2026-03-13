package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@code @function} rule.
 */
public final class FunctionRule extends CallableDeclaration {

    private final FileSpan span;

    public FunctionRule(
            String name,
            String originalName,
            ArgumentDeclaration parameters,
            @Nullable SilentComment comment,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(name, originalName, parameters, comment, children, hasDeclarations);
        this.span = span;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitFunctionRule(this);
    }
}
