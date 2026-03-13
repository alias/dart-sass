package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A CSS property declaration: {@code name: value}.
 *
 * <p>Children may be {@code null} for simple declarations without nested blocks.
 */
public final class Declaration extends ParentStatement {

    private final Interpolation name;
    private final @Nullable Expression value;
    private final boolean parsedAsSassScript;
    private final FileSpan span;

    public Declaration(
            Interpolation name,
            @Nullable Expression value,
            boolean parsedAsSassScript,
            @Nullable List<Statement> children,
            boolean hasDeclarations,
            FileSpan span) {
        super(children, hasDeclarations);
        this.name = name;
        this.value = value;
        this.parsedAsSassScript = parsedAsSassScript;
        this.span = span;
    }

    public Interpolation getName() {
        return name;
    }

    public @Nullable Expression getValue() {
        return value;
    }

    public boolean isParsedAsSassScript() {
        return parsedAsSassScript;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitDeclaration(this);
    }
}
