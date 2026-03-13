package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A variable reference expression ({@code $name}).
 */
public final class VariableExpression extends Expression {
    private final @Nullable String namespace;
    private final String name;
    private final FileSpan span;

    public VariableExpression(String name, @Nullable String namespace, FileSpan span) {
        this.name = name;
        this.namespace = namespace;
        this.span = span;
    }

    public VariableExpression(String name, FileSpan span) {
        this(name, null, span);
    }

    /** The namespace of this variable, or null if it's not namespaced. */
    public @Nullable String getNamespace() {
        return namespace;
    }

    /** The name of this variable, without the leading {@code $}. */
    public String getName() {
        return name;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitVariableExpression(this);
    }
}
