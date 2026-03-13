package com.sass.ast.selector;

import com.sass.ast.SassNode;
import com.sass.visitor.SelectorVisitor;

/**
 * A node in a selector abstract syntax tree.
 *
 * <p>This is the base class for all selector types: simple selectors, compound
 * selectors, complex selectors, and selector lists.</p>
 */
public abstract class Selector implements SassNode {

    /**
     * Calls the appropriate visit method on {@code visitor}.
     *
     * @param visitor the selector visitor
     * @param <T> the return type of the visitor
     * @return the result of the visit method
     */
    public abstract <T> T accept(SelectorVisitor<T> visitor);

    /**
     * Returns whether this selector is invisible and should not be emitted
     * in the output CSS. Defaults to {@code false}.
     */
    public boolean isInvisible() {
        return false;
    }

    /**
     * Returns whether this selector contains a parent selector ({@code &}).
     * Defaults to {@code false}.
     */
    public boolean containsParentSelector() {
        return false;
    }
}
