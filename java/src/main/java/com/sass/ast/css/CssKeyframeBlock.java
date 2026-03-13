package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

import java.util.List;

/**
 * A block within a CSS {@code @keyframes} rule.
 *
 * <p>For example, in {@code @keyframes { from { opacity: 0 } }},
 * the {@code from { opacity: 0 }} part is a keyframe block.</p>
 */
public final class CssKeyframeBlock extends CssParentNode {

    private final CssValue<List<String>> selector;
    private final FileSpan span;

    /**
     * Creates a new CSS keyframe block.
     *
     * @param selector the keyframe selector (e.g., "from", "50%", "to")
     * @param span the source span for the block
     */
    public CssKeyframeBlock(CssValue<List<String>> selector, FileSpan span) {
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
}
