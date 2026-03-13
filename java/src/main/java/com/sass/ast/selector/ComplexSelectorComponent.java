package com.sass.ast.selector;

import com.sass.ast.SassNode;
import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;

import java.util.List;
import java.util.Objects;

/**
 * A component of a complex selector, consisting of a compound selector and
 * its trailing combinators.
 *
 * <p>This is a data holder and is not itself a {@link Selector}.</p>
 */
public final class ComplexSelectorComponent implements SassNode {

    private final CompoundSelector selector;
    private final List<CssValue<Combinator>> combinators;
    private final FileSpan span;

    /**
     * Creates a complex selector component.
     *
     * @param selector the compound selector
     * @param combinators the trailing combinators
     * @param span the source span
     */
    public ComplexSelectorComponent(CompoundSelector selector,
                                   List<CssValue<Combinator>> combinators,
                                   FileSpan span) {
        this.selector = Objects.requireNonNull(selector);
        this.combinators = List.copyOf(combinators);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the compound selector. */
    public CompoundSelector getSelector() {
        return selector;
    }

    /** Returns the trailing combinators. */
    public List<CssValue<Combinator>> getCombinators() {
        return combinators;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComplexSelectorComponent other)) return false;
        return selector.equals(other.selector)
                && combinators.equals(other.combinators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, combinators);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(selector);
        for (CssValue<Combinator> combinator : combinators) {
            sb.append(' ').append(combinator.getValue());
        }
        return sb.toString();
    }
}
