package com.sass.ast.css.modifiable;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A mutable CSS stylesheet used during Sass evaluation.
 *
 * <p>This is the root node of the mutable CSS AST built up during
 * evaluation.</p>
 */
public final class ModifiableCssStylesheet extends ModifiableCssParentNode {

    private final FileSpan span;

    /**
     * Creates a new modifiable CSS stylesheet.
     *
     * @param span the source span for the stylesheet
     */
    public ModifiableCssStylesheet(FileSpan span) {
        this.span = span;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssStylesheet(this);
    }

    @Override
    public ModifiableCssStylesheet copyWithoutChildren() {
        return new ModifiableCssStylesheet(span);
    }
}
