package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * A loud (multi-line) comment: {@code /* ... *}{@code /}.
 */
public final class LoudComment extends Statement {

    private final Interpolation text;
    private final FileSpan span;

    public LoudComment(Interpolation text) {
        this.text = text;
        this.span = text.getSpan();
    }

    public Interpolation getText() {
        return text;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitLoudComment(this);
    }
}
