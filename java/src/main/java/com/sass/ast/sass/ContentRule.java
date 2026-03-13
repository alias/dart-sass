package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * An {@code @content} rule within a mixin body.
 */
public final class ContentRule extends Statement {

    private final ArgumentInvocation arguments;
    private final FileSpan span;

    public ContentRule(ArgumentInvocation arguments, FileSpan span) {
        this.arguments = arguments;
        this.span = span;
    }

    public ArgumentInvocation getArguments() {
        return arguments;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitContentRule(this);
    }
}
