package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * The root node of a Sass or CSS stylesheet.
 */
public final class Stylesheet extends ParentStatement {

    private final boolean plainCss;
    private final FileSpan span;

    public Stylesheet(List<Statement> children, FileSpan span, boolean plainCss) {
        super(children, false);
        this.span = span;
        this.plainCss = plainCss;
    }

    public boolean isPlainCss() {
        return plainCss;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitStylesheet(this);
    }
}
