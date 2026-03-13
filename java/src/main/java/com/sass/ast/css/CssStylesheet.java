package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.util.SourceFile;
import com.sass.visitor.CssVisitor;

import java.net.URI;

/**
 * A plain CSS stylesheet.
 *
 * <p>This is the root node of a compiled CSS AST. It contains all the
 * top-level CSS nodes produced by evaluating a Sass stylesheet.</p>
 */
public final class CssStylesheet extends CssParentNode {

    private final FileSpan span;

    /**
     * Creates a new CSS stylesheet with the given source span.
     *
     * @param span the source span for the entire stylesheet
     */
    public CssStylesheet(FileSpan span) {
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

    /**
     * Creates an empty stylesheet for the given URL.
     *
     * @param url the URL of the stylesheet source
     * @return an empty CSS stylesheet
     */
    public static CssStylesheet empty(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        var sourceFile = new SourceFile("", uri);
        var span = sourceFile.span(0, 0);
        return new CssStylesheet(span);
    }
}
