package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A function invocation expression.
 *
 * <p>This is used for plain CSS functions as well as Sass functions that are
 * resolved by name (e.g., {@code lighten()}, {@code my-function()}).
 */
public final class FunctionExpression extends Expression {
    private final @Nullable String namespace;
    private final String name;
    private final String originalName;
    private final ArgumentInvocation arguments;
    private final FileSpan span;

    public FunctionExpression(String name, String originalName,
                              ArgumentInvocation arguments, @Nullable String namespace,
                              FileSpan span) {
        this.name = name;
        this.originalName = originalName;
        this.arguments = arguments;
        this.namespace = namespace;
        this.span = span;
    }

    public FunctionExpression(String name, ArgumentInvocation arguments, FileSpan span) {
        this(name, name, arguments, null, span);
    }

    /** The namespace of this function, or null if it's not namespaced. */
    public @Nullable String getNamespace() {
        return namespace;
    }

    /** The name of this function, after underscores have been normalized to hyphens. */
    public String getName() {
        return name;
    }

    /** The original name of this function as it appeared in the source. */
    public String getOriginalName() {
        return originalName;
    }

    /** The arguments to this function. */
    public ArgumentInvocation getArguments() {
        return arguments;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitFunctionExpression(this);
    }
}
