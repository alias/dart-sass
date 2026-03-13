package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@code @mixin} rule.
 */
public final class MixinRule extends CallableDeclaration {

    private final boolean hasContent;
    private final FileSpan span;

    public MixinRule(
            String name,
            String originalName,
            ArgumentDeclaration parameters,
            @Nullable SilentComment comment,
            boolean hasContent,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(name, originalName, parameters, comment, children, hasDeclarations);
        this.hasContent = hasContent;
        this.span = span;
    }

    /** Whether this mixin contains an {@code @content} directive. */
    public boolean hasContent() {
        return hasContent;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitMixinRule(this);
    }
}
