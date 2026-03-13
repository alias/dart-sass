package com.sass.ast.selector;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple selector — a single component of a compound selector.
 *
 * <p>Simple selectors include type selectors, universal selectors, class
 * selectors, ID selectors, attribute selectors, and pseudo selectors.</p>
 */
public abstract class SimpleSelector extends Selector {

    /**
     * Returns the specificity of this selector.
     *
     * <p>Specificity is calculated as:
     * <ul>
     *   <li>Type selectors: 1</li>
     *   <li>Class selectors, attribute selectors, pseudo-classes: 1000</li>
     *   <li>ID selectors: 1000000</li>
     * </ul>
     *
     * <p>The default implementation returns 1000 (class-level specificity).
     */
    public int getSpecificity() {
        return 1000;
    }

    /**
     * Returns a copy of this simple selector with the given {@code suffix}
     * added to the end.
     *
     * <p>By default, this throws an {@link UnsupportedOperationException}
     * because most simple selectors do not support suffixes.</p>
     *
     * @param suffix the suffix to add
     * @return a new simple selector with the suffix appended
     * @throws UnsupportedOperationException if this selector does not support suffixes
     */
    public SimpleSelector addSuffix(String suffix) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support addSuffix().");
    }

    /**
     * Unifies this simple selector with a list of other simple selectors.
     *
     * <p>Returns a new list of simple selectors that matches only elements
     * matched by both this selector and the given {@code compound} list, or
     * {@code null} if no such list exists.</p>
     *
     * <p>The default implementation appends this selector to the list if it is
     * not already present.</p>
     *
     * @param compound the compound selector to unify with
     * @return the unified compound selector, or null if unification is impossible
     */
    public List<SimpleSelector> unify(List<SimpleSelector> compound) {
        List<SimpleSelector> result = new ArrayList<>(compound);
        if (!result.contains(this)) {
            result.add(this);
        }
        return result;
    }

    /**
     * Returns whether this selector is a superselector of {@code other}.
     *
     * <p>The default implementation checks for equality.</p>
     *
     * @param other the other selector
     * @return true if this is a superselector of other
     */
    public boolean isSuperselector(SimpleSelector other) {
        return this.equals(other);
    }
}
