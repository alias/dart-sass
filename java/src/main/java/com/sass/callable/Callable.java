package com.sass.callable;

/**
 * An interface for functions and mixins that can be invoked from Sass by
 * passing in arguments.
 *
 * <p>This is the base interface for all callable types in Sass, including
 * built-in functions, user-defined functions and mixins, and plain CSS
 * function calls.
 */
public interface Callable {

    /** The callable's name. */
    String getName();
}
