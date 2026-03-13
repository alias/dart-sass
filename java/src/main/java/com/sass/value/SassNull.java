package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

/**
 * The SassScript null value.
 */
public final class SassNull extends Value {
    /** The singleton null value. */
    public static final SassNull INSTANCE = new SassNull();

    private SassNull() {}

    @Override
    public boolean isTruthy() { return false; }

    @Override
    public boolean isBlank() { return true; }

    @Override
    public @Nullable Value realNull() { return null; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitNull();
    }

    @Override
    public Value unaryNot() {
        return SassBoolean.TRUE;
    }

    @Override
    public int hashCode() { return 0; }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassNull;
    }

    @Override
    public String toString() { return "null"; }
}
