package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A SassScript function reference.
 * <p>
 * The actual callable is stored opaquely; it will be properly typed
 * once the callable infrastructure is implemented.
 */
public final class SassFunction extends Value {
    private final Object callable; // Will be typed as Callable once implemented

    public SassFunction(Object callable) {
        this.callable = callable;
    }

    /** Returns the callable wrapped by this function reference. */
    public Object getCallable() { return callable; }

    @Override
    public SassFunction assertFunction(@Nullable String name) { return this; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitFunction(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassFunction f && callable.equals(f.callable);
    }

    @Override
    public int hashCode() { return callable.hashCode(); }

    @Override
    public String toString() { return "get-function(\"" + callable + "\")"; }
}
