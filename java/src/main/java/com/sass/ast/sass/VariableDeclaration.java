package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A variable declaration: {@code $name: expression}.
 */
public final class VariableDeclaration extends Statement {

    private final @Nullable String namespace;
    private final String name;
    private final Expression expression;
    private final boolean isGuarded;
    private final boolean isGlobal;
    private final FileSpan span;

    public VariableDeclaration(
            @Nullable String namespace,
            String name,
            Expression expression,
            boolean isGuarded,
            boolean isGlobal,
            FileSpan span) {
        this.namespace = namespace;
        this.name = name;
        this.expression = expression;
        this.isGuarded = isGuarded;
        this.isGlobal = isGlobal;
        this.span = span;
    }

    public @Nullable String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public Expression getExpression() {
        return expression;
    }

    public boolean isGuarded() {
        return isGuarded;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitVariableDeclaration(this);
    }
}
