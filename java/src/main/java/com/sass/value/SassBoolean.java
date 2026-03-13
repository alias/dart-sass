package com.sass.value;

import com.sass.visitor.ValueVisitor;

/**
 * A SassScript boolean value.
 */
public final class SassBoolean extends Value {
    /** The SassScript {@code true} value. */
    public static final SassBoolean TRUE = new SassBoolean(true);

    /** The SassScript {@code false} value. */
    public static final SassBoolean FALSE = new SassBoolean(false);

    private final boolean value;

    private SassBoolean(boolean value) {
        this.value = value;
    }

    /** Returns the SassBoolean for the given Java boolean. */
    public static SassBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** Whether this value is true or false. */
    public boolean getValue() { return value; }

    @Override
    public boolean isTruthy() { return value; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitBoolean(this);
    }

    @Override
    public SassBoolean assertBoolean(String name) { return this; }

    @Override
    public Value unaryNot() {
        return value ? FALSE : TRUE;
    }

    @Override
    public int hashCode() { return Boolean.hashCode(value); }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassBoolean sb && sb.value == this.value;
    }

    @Override
    public String toString() { return value ? "true" : "false"; }
}
