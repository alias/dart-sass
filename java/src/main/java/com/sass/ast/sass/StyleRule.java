package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * A style rule (selector + declaration block).
 */
public final class StyleRule extends ParentStatement {

    private final Interpolation selector;
    private final FileSpan span;

    public StyleRule(Interpolation selector, List<Statement> children,
                     boolean hasDeclarations, FileSpan span) {
        super(children, hasDeclarations);
        this.selector = selector;
        this.span = span;
    }

    public Interpolation getSelector() {
        return selector;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitStyleRule(this);
    }
}
