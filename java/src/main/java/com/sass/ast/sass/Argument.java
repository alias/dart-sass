package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;
import org.jspecify.annotations.Nullable;

/**
 * An argument in an argument declaration (e.g., {@code $name: default}).
 */
public final class Argument implements SassNode {

    private final String name;
    private final @Nullable Expression defaultValue;
    private final FileSpan span;

    public Argument(String name, @Nullable Expression defaultValue, FileSpan span) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.span = span;
    }

    public String getName() {
        return name;
    }

    public @Nullable Expression getDefaultValue() {
        return defaultValue;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }
}
