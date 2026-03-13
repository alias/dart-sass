package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * A silent (single-line) comment: {@code // ...}.
 */
public final class SilentComment extends Statement {

    private final String text;
    private final FileSpan span;

    public SilentComment(String text, FileSpan span) {
        this.text = text;
        this.span = span;
    }

    public String getText() {
        return text;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitSilentComment(this);
    }
}
