package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A plain CSS {@code @supports} rule.
 */
public final class CssSupportsRule extends CssParentNode {

    private final CssValue<String> condition;
    private final FileSpan span;

    /**
     * Creates a new CSS supports rule.
     *
     * @param condition the supports condition
     * @param span the source span for the rule
     */
    public CssSupportsRule(CssValue<String> condition, FileSpan span) {
        this.condition = condition;
        this.span = span;
    }

    /** Returns the supports condition. */
    public CssValue<String> getCondition() {
        return condition;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssSupportsRule(this);
    }
}
