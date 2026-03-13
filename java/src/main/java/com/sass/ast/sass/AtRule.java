package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An unknown at-rule.
 */
public final class AtRule extends ParentStatement {

    private final Interpolation name;
    private final @Nullable Interpolation value;
    private final boolean isChildless;
    private final FileSpan span;

    public AtRule(
            Interpolation name,
            @Nullable Interpolation value,
            boolean isChildless,
            @Nullable List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(children, hasDeclarations);
        this.name = name;
        this.value = value;
        this.isChildless = isChildless;
        this.span = span;
    }

    public Interpolation getName() {
        return name;
    }

    public @Nullable Interpolation getValue() {
        return value;
    }

    public boolean isChildless() {
        return isChildless;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitAtRule(this);
    }
}
