package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * An anonymous block of content passed to a mixin via {@code @content}.
 */
public final class ContentBlock extends CallableDeclaration {

    private final FileSpan span;

    public ContentBlock(
            ArgumentDeclaration parameters,
            List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super("@content", "@content", parameters, null, children, hasDeclarations);
        this.span = span;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitContentBlock(this);
    }
}
