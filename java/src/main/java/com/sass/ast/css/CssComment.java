package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

/**
 * A plain CSS comment.
 *
 * <p>This represents a {@code /* ... * /} comment in the CSS output.
 * Comments beginning with {@code /*!} are preserved even in compressed mode.</p>
 */
public final class CssComment extends CssNode {

    private final String text;
    private final FileSpan span;

    /**
     * Creates a new CSS comment.
     *
     * @param text the full text of the comment, including delimiters
     * @param span the source span for the comment
     */
    public CssComment(String text, FileSpan span) {
        this.text = text;
        this.span = span;
    }

    /** Returns the full comment text, including delimiters. */
    public String getText() {
        return text;
    }

    /**
     * Returns whether this comment should be preserved even in compressed
     * output. This is true for comments that begin with {@code /*!}.
     */
    public boolean isPreserved() {
        return text.length() > 2 && text.charAt(2) == '!';
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssComment(this);
    }
}
