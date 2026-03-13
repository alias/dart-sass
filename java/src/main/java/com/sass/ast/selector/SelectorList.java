package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.List;
import java.util.Objects;

/**
 * A selector list — a comma-separated list of complex selectors
 * (e.g. {@code .foo, .bar > .baz}).
 */
public final class SelectorList extends Selector {

    private final List<ComplexSelector> components;
    private final FileSpan span;

    /**
     * Creates a selector list.
     *
     * @param components the complex selectors in this list (must not be empty)
     * @param span the source span
     * @throws IllegalArgumentException if components is empty
     */
    public SelectorList(List<ComplexSelector> components, FileSpan span) {
        if (components.isEmpty()) {
            throw new IllegalArgumentException("SelectorList must have at least one component.");
        }
        this.components = List.copyOf(components);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the complex selectors in this list. */
    public List<ComplexSelector> getComponents() {
        return components;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /**
     * Returns {@code true} if all components of this selector list are invisible.
     */
    @Override
    public boolean isInvisible() {
        for (ComplexSelector component : components) {
            if (!component.isInvisible()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsParentSelector() {
        for (ComplexSelector component : components) {
            if (component.containsParentSelector()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitSelectorList(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectorList other)) return false;
        return components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(components.get(i));
        }
        return sb.toString();
    }
}
