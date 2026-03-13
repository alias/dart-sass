package com.sass.ast.css;

import com.sass.util.FileSpan;

import java.util.Objects;

/**
 * A wrapper that pairs a value with its source span.
 *
 * <p>This is used throughout the CSS AST to track where values (such as
 * selector strings, property names, and at-rule names) originated in the
 * source.</p>
 *
 * <p>Equality and hashing are based on the wrapped value only, not the span.</p>
 *
 * @param <T> the type of the wrapped value
 */
public final class CssValue<T> {

    private final T value;
    private final FileSpan span;

    /**
     * Creates a new CSS value wrapper.
     *
     * @param value the wrapped value
     * @param span the source span of the value
     */
    public CssValue(T value, FileSpan span) {
        this.value = Objects.requireNonNull(value);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the wrapped value. */
    public T getValue() {
        return value;
    }

    /** Returns the source span where this value appeared. */
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CssValue<?> other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
