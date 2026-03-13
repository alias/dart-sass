package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

import java.util.List;

/**
 * A mutable block within a CSS {@code @keyframes} rule, used during
 * Sass evaluation.
 */
public final class ModifiableCssKeyframeBlock extends ModifiableCssParentNode {

    private final CssValue<List<String>> selector;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS keyframe block.
     *
     * @param selector the keyframe selector (e.g., "from", "50%", "to")
     * @param span the source span for the block
     */
    public ModifiableCssKeyframeBlock(CssValue<List<String>> selector, FileSpan span) {
        this.selector = selector;
        this.span = span;
    }

    /** Returns the keyframe selector. */
    public CssValue<List<String>> getSelector() {
        return selector;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssKeyframeBlock(this);
    }

    @Override
    public ModifiableCssKeyframeBlock copyWithoutChildren() {
        return new ModifiableCssKeyframeBlock(selector, span);
    }
}
