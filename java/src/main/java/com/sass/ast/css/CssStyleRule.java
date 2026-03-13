package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A plain CSS style rule.
 *
 * <p>This contains a selector and a list of declarations and nested rules.</p>
 */
public final class CssStyleRule extends CssParentNode {

    private final CssValue<String> selector;
    private final FileSpan span;
    private final boolean fromPlainCss;

    /**
     * Creates a new CSS style rule.
     *
     * @param selector the rule's selector
     * @param span the source span for the rule
     * @param fromPlainCss whether this rule was parsed from a plain CSS stylesheet
     */
    public CssStyleRule(CssValue<String> selector, FileSpan span, boolean fromPlainCss) {
        this.selector = selector;
        this.span = span;
        this.fromPlainCss = fromPlainCss;
    }

    /**
     * Creates a new CSS style rule that was not parsed from plain CSS.
     */
    public CssStyleRule(CssValue<String> selector, FileSpan span) {
        this(selector, span, false);
    }

    /** Returns the selector for this rule. */
    public CssValue<String> getSelector() {
        return selector;
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
}
