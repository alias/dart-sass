package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

/**
 * An {@code @include} rule that invokes a mixin.
 */
public final class IncludeRule extends Statement {

    private final @Nullable String namespace;
    private final String name;
    private final String originalName;
    private final ArgumentInvocation arguments;
    private final @Nullable ContentBlock content;
    private final FileSpan span;

    public IncludeRule(
            @Nullable String namespace,
            String name,
            String originalName,
            ArgumentInvocation arguments,
            @Nullable ContentBlock content,
            FileSpan span) {
        this.namespace = namespace;
        this.name = name;
        this.originalName = originalName;
        this.arguments = arguments;
        this.content = content;
        this.span = span;
    }

    public @Nullable String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getOriginalName() {
        return originalName;
    }

    public ArgumentInvocation getArguments() {
        return arguments;
    }

    public @Nullable ContentBlock getContent() {
        return content;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitIncludeRule(this);
    }
}
