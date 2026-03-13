package com.sass.callable;

/**
 * A {@link Callable} that is directly visible to users of the Sass compiler.
 *
 * <p>This marker interface distinguishes user-facing callables (built-in functions
 * and user-defined functions/mixins) from internal-only callables like
 * {@link PlainCssCallable}.
 */
public interface SassCallable extends Callable {
}
