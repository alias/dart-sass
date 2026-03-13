package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A mutable CSS at-rule used during Sass evaluation.
 */
public final class ModifiableCssAtRule extends ModifiableCssParentNode {

    private final CssValue<String> name;
    private final @Nullable CssValue<String> value;
    private final boolean isChildless;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS at-rule.
     *
     * @param name the at-rule name
     * @param value the at-rule value, or null if none
     * @param isChildless whether this at-rule cannot have children
     * @param span the source span for the rule
     */
    public ModifiableCssAtRule(CssValue<String> name, @Nullable CssValue<String> value,
                               boolean isChildless, FileSpan span) {
        this.name = name;
        this.value = value;
        this.isChildless = isChildless;
        this.span = span;
    }

    /** Returns the at-rule name. */
    public CssValue<String> getName() {
        return name;
    }

    /** Returns the at-rule value, or null if none. */
    public @Nullable CssValue<String> getValue() {
        return value;
    }

    /** Returns whether this at-rule cannot have children. */
    public boolean isChildless() {
        return isChildless;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssAtRule(this);
    }

    @Override
    public ModifiableCssAtRule copyWithoutChildren() {
        return new ModifiableCssAtRule(name, value, isChildless, span);
    }
}
