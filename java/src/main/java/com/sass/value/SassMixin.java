package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A SassScript mixin reference.
 * <p>
 * The actual callable is stored opaquely; it will be properly typed
 * once the callable infrastructure is implemented.
 */
public final class SassMixin extends Value {
    private final Object callable; // Will be typed as Callable once implemented

    public SassMixin(Object callable) {
        this.callable = callable;
    }

    /** Returns the callable wrapped by this mixin reference. */
    public Object getCallable() { return callable; }

    @Override
    public SassMixin assertMixin(@Nullable String name) { return this; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitMixin(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassMixin m && callable.equals(m.callable);
    }

    @Override
    public int hashCode() { return callable.hashCode(); }

    @Override
    public String toString() { return "get-mixin(\"" + callable + "\")"; }
}
