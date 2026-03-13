package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

/**
 * An {@code @extend} rule.
 */
public final class ExtendRule extends Statement {

    private final Interpolation selector;
    private final boolean isOptional;
    private final FileSpan span;

    public ExtendRule(Interpolation selector, boolean isOptional, FileSpan span) {
        this.selector = selector;
        this.isOptional = isOptional;
        this.span = span;
    }

    public Interpolation getSelector() {
        return selector;
    }

    public boolean isOptional() {
        return isOptional;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitExtendRule(this);
    }
}
