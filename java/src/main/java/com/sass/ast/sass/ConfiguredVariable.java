package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;

/**
 * A variable configured in a {@code @use} or {@code @forward} rule
 * (e.g., {@code $name: value}).
 */
public final class ConfiguredVariable implements SassNode {

    private final String name;
    private final Expression expression;
    private final boolean isGuarded;
    private final FileSpan span;

    public ConfiguredVariable(String name, Expression expression, boolean isGuarded, FileSpan span) {
        this.name = name;
        this.expression = expression;
        this.isGuarded = isGuarded;
        this.span = span;
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

    @Override
    public FileSpan getSpan() {
        return span;
    }
}
