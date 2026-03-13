package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * A function-syntax supports condition.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/function.dart}.
 */
public final class SupportsFunction implements SupportsCondition {

    /** The name of the function. */
    private final Interpolation name;

    /** The arguments to the function. */
    private final Interpolation arguments;

    private final FileSpan span;

    public SupportsFunction(Interpolation name, Interpolation arguments, FileSpan span) {
        this.name = name;
        this.arguments = arguments;
        this.span = span;
    }

    /** Returns the name of the function. */
    public Interpolation getName() {
        return name;
    }

    /** Returns the arguments to the function. */
    public Interpolation getArguments() {
        return arguments;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsFunction withSpan(FileSpan span) {
        return new SupportsFunction(name, arguments, span);
    }

    @Override
    public String toString() {
        return name + "(" + arguments + ")";
    }
}
