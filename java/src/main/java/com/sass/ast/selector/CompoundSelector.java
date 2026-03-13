package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.List;
import java.util.Objects;

/**
 * A compound selector — a sequence of simple selectors that are all applied to
 * the same element (e.g. {@code div.foo#bar}).
 */
public final class CompoundSelector extends Selector {

    private final List<SimpleSelector> components;
    private final FileSpan span;

    /**
     * Creates a compound selector.
     *
     * @param components the simple selectors in this compound selector (must not be empty)
     * @param span the source span
     * @throws IllegalArgumentException if components is empty
     */
    public CompoundSelector(List<SimpleSelector> components, FileSpan span) {
        if (components.isEmpty()) {
            throw new IllegalArgumentException("CompoundSelector must have at least one component.");
        }
        this.components = List.copyOf(components);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the simple selectors that make up this compound selector. */
    public List<SimpleSelector> getComponents() {
        return components;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /**
     * Returns the specificity of this compound selector, which is the sum of
     * the specificities of its components.
     */
    public int getSpecificity() {
        int specificity = 0;
        for (SimpleSelector component : components) {
            specificity += component.getSpecificity();
        }
        return specificity;
    }

    @Override
    public boolean isInvisible() {
        for (SimpleSelector component : components) {
            if (component.isInvisible()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsParentSelector() {
        for (SimpleSelector component : components) {
            if (component.containsParentSelector()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitCompoundSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompoundSelector other)) return false;
        return components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (SimpleSelector component : components) {
            sb.append(component);
        }
        return sb.toString();
    }
}
