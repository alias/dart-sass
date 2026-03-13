package com.sass.ast.selector;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A complex selector — a sequence of compound selectors separated by
 * combinators (e.g. {@code .foo > .bar + .baz}).
 */
public final class ComplexSelector extends Selector {

    private final List<CssValue<Combinator>> leadingCombinators;
    private final List<ComplexSelectorComponent> components;
    private final boolean lineBreak;
    private final FileSpan span;

    /**
     * Creates a complex selector.
     *
     * @param leadingCombinators combinators before the first compound selector
     * @param components the compound selectors and their trailing combinators
     * @param lineBreak whether this selector should be printed on a new line
     * @param span the source span
     */
    public ComplexSelector(List<CssValue<Combinator>> leadingCombinators,
                           List<ComplexSelectorComponent> components,
                           boolean lineBreak, FileSpan span) {
        this.leadingCombinators = List.copyOf(leadingCombinators);
        this.components = List.copyOf(components);
        this.lineBreak = lineBreak;
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the combinators before the first compound selector. */
    public List<CssValue<Combinator>> getLeadingCombinators() {
        return leadingCombinators;
    }

    /** Returns the compound selector components. */
    public List<ComplexSelectorComponent> getComponents() {
        return components;
    }

    /** Returns whether this selector should be printed on a new line. */
    public boolean isLineBreak() {
        return lineBreak;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /**
     * Returns the specificity of this complex selector, which is the sum of
     * the specificities of all its compound selector components.
     */
    public int getSpecificity() {
        int specificity = 0;
        for (ComplexSelectorComponent component : components) {
            specificity += component.getSelector().getSpecificity();
        }
        return specificity;
    }

    /**
     * If this complex selector contains only a single compound selector with
     * no combinators, returns that compound selector. Otherwise returns null.
     *
     * @return the single compound selector, or null
     */
    public @Nullable CompoundSelector singleCompound() {
        if (!leadingCombinators.isEmpty()) {
            return null;
        }
        if (components.size() != 1) {
            return null;
        }
        ComplexSelectorComponent component = components.get(0);
        if (!component.getCombinators().isEmpty()) {
            return null;
        }
        return component.getSelector();
    }

    @Override
    public boolean isInvisible() {
        for (ComplexSelectorComponent component : components) {
            if (component.getSelector().isInvisible()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsParentSelector() {
        for (ComplexSelectorComponent component : components) {
            if (component.getSelector().containsParentSelector()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitComplexSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComplexSelector other)) return false;
        return leadingCombinators.equals(other.leadingCombinators)
                && components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leadingCombinators, components);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (CssValue<Combinator> combinator : leadingCombinators) {
            sb.append(combinator.getValue()).append(' ');
        }
        for (int i = 0; i < components.size(); i++) {
            ComplexSelectorComponent component = components.get(i);
            sb.append(component.getSelector());
            for (CssValue<Combinator> combinator : component.getCombinators()) {
                sb.append(' ').append(combinator.getValue());
            }
            if (i < components.size() - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
