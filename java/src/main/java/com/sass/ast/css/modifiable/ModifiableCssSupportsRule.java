package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A mutable CSS {@code @supports} rule used during Sass evaluation.
 */
public final class ModifiableCssSupportsRule extends ModifiableCssParentNode {

    private final CssValue<String> condition;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS supports rule.
     *
     * @param condition the supports condition
     * @param span the source span for the rule
     */
    public ModifiableCssSupportsRule(CssValue<String> condition, FileSpan span) {
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

    @Override
    public ModifiableCssSupportsRule copyWithoutChildren() {
        return new ModifiableCssSupportsRule(condition, span);
    }
}
