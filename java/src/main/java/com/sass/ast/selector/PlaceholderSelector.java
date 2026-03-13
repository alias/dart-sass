package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.Objects;

/**
 * A placeholder selector (e.g. {@code %foo}).
 *
 * <p>Placeholder selectors are used with {@code @extend} and never appear in
 * the output CSS. They are always invisible.</p>
 */
public final class PlaceholderSelector extends SimpleSelector {

    private final String name;
    private final FileSpan span;

    public PlaceholderSelector(String name, FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the placeholder name (without the leading {@code %}). */
    public String getName() {
        return name;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /** Returns {@code true} if this is a private placeholder (name starts with '-' or '_'). */
    public boolean isPrivate() {
        return !name.isEmpty() && (name.charAt(0) == '-' || name.charAt(0) == '_');
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public SimpleSelector addSuffix(String suffix) {
        return new PlaceholderSelector(name + suffix, span);
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitPlaceholderSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaceholderSelector other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "%" + name;
    }
}
