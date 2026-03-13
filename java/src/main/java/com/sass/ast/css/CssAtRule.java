package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A plain CSS at-rule.
 *
 * <p>This represents an unknown at-rule that is not specially handled
 * (unlike {@code @media} or {@code @supports}).</p>
 */
public final class CssAtRule extends CssParentNode {

    private final CssValue<String> name;
    private final @Nullable CssValue<String> value;
    private final boolean isChildless;
    private final FileSpan span;

    /**
     * Creates a new CSS at-rule.
     *
     * @param name the at-rule name (e.g., "charset", "font-face")
     * @param value the at-rule value, or null if none
     * @param isChildless whether this at-rule cannot have children
     * @param span the source span for the rule
     */
    public CssAtRule(CssValue<String> name, @Nullable CssValue<String> value,
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

    @Override
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
}
