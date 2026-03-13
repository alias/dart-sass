package com.sass.exception;

import com.sass.util.FileSpan;

/**
 * An exception thrown by SassScript.
 * <p>
 * This doesn't extend {@link SassException} because it doesn't have a
 * {@link FileSpan} associated with it. It's caught by Sass's internals and
 * converted to a {@link SassRuntimeException} with a source span and stack trace.
 */
public class SassScriptException extends RuntimeException {

    /**
     * Creates a SassScriptException with the given message.
     *
     * @param message the error message
     * @param argumentName the name of the function argument that triggered this, or null
     */
    public SassScriptException(String message, String argumentName) {
        super(argumentName == null ? message : "$" + argumentName + ": " + message);
    }

    public SassScriptException(String message) {
        this(message, (String) null);
    }

    /**
     * Converts this to a SassException with the given span.
     */
    public SassException withSpan(FileSpan span) {
        return new SassException(getMessage(), span);
    }
}
