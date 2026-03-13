package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A parent selector ({@code &}), optionally with a suffix.
 *
 * <p>The parent selector refers to the enclosing selector in nested rules.
 * An optional suffix can be appended when the selector is resolved
 * (e.g. {@code &-active}).</p>
 */
public final class ParentSelector extends SimpleSelector {

    private final @Nullable String suffix;
    private final FileSpan span;

    public ParentSelector(@Nullable String suffix, FileSpan span) {
        this.suffix = suffix;
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the suffix added after resolution, or null if none. */
    public @Nullable String getSuffix() {
        return suffix;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public boolean containsParentSelector() {
        return true;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitParentSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParentSelector other)) return false;
        return Objects.equals(suffix, other.suffix);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(suffix);
    }

    @Override
    public String toString() {
        return suffix != null ? "&" + suffix : "&";
    }
}
