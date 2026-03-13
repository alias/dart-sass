package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A mutable CSS style rule used during Sass evaluation.
 *
 * <p>The selector is mutable because {@code @extend} may modify it
 * after the rule is created.</p>
 */
public final class ModifiableCssStyleRule extends ModifiableCssParentNode {

    private CssValue<String> selector;
    private final CssValue<String> originalSelector;
    private final boolean fromPlainCss;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS style rule.
     *
     * @param selector the rule's selector
     * @param span the source span for the rule
     * @param fromPlainCss whether this rule was parsed from a plain CSS stylesheet
     */
    public ModifiableCssStyleRule(CssValue<String> selector, FileSpan span,
                                  boolean fromPlainCss) {
        this.selector = selector;
        this.originalSelector = selector;
        this.span = span;
        this.fromPlainCss = fromPlainCss;
    }

    /**
     * Creates a new modifiable CSS style rule that was not from plain CSS.
     */
    public ModifiableCssStyleRule(CssValue<String> selector, FileSpan span) {
        this(selector, span, false);
    }

    /** Returns the current selector (may have been modified by {@code @extend}). */
    public CssValue<String> getSelector() {
        return selector;
    }

    /**
     * Sets the selector for this rule.
     * This is used by the {@code @extend} implementation.
     *
     * @param selector the new selector
     */
    public void setSelector(CssValue<String> selector) {
        this.selector = selector;
    }

    /** Returns the original selector before any {@code @extend} modifications. */
    public CssValue<String> getOriginalSelector() {
        return originalSelector;
    }

    /** Returns whether this rule was parsed from a plain CSS stylesheet. */
    public boolean isFromPlainCss() {
        return fromPlainCss;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssStyleRule(this);
    }

    @Override
    public ModifiableCssStyleRule copyWithoutChildren() {
        return new ModifiableCssStyleRule(selector, span, fromPlainCss);
    }
}
