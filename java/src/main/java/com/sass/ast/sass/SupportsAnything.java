package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * A supports condition that represents the forwards-compatible
 * {@code <general-enclosed>} production.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/anything.dart}.
 */
public final class SupportsAnything implements SupportsCondition {

    /** The contents of the condition. */
    private final Interpolation contents;

    private final FileSpan span;

    public SupportsAnything(Interpolation contents, FileSpan span) {
        this.contents = contents;
        this.span = span;
    }

    /** Returns the contents of the condition. */
    public Interpolation getContents() {
        return contents;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsAnything withSpan(FileSpan span) {
        return new SupportsAnything(contents, span);
    }

    @Override
    public String toString() {
        return "(" + contents + ")";
    }
}
