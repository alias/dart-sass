package com.sass.callable;

import java.util.Objects;

/**
 * A callable that emits a plain CSS function.
 *
 * <p>This represents a CSS function call that is not a Sass function -- it is
 * passed through to the output CSS unchanged. This can't be used for mixins.
 */
public final class PlainCssCallable implements Callable {

    private final String name;

    public PlainCssCallable(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlainCssCallable that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name + "()";
    }
}
