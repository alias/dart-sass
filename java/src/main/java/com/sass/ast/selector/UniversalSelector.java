package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A universal selector ({@code *} or {@code ns|*}).
 */
public final class UniversalSelector extends SimpleSelector {

    private final @Nullable String namespace;
    private final FileSpan span;

    public UniversalSelector(@Nullable String namespace, FileSpan span) {
        this.namespace = namespace;
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the namespace, or null for the default namespace. */
    public @Nullable String getNamespace() {
        return namespace;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public int getSpecificity() {
        return 0;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitUniversalSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UniversalSelector other)) return false;
        return Objects.equals(namespace, other.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace);
    }

    @Override
    public String toString() {
        if (namespace == null) {
            return "*";
        }
        return namespace + "|*";
    }
}
