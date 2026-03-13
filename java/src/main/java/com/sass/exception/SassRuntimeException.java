package com.sass.exception;

import com.sass.util.FileSpan;

import java.net.URI;
import java.util.Set;

/**
 * An exception thrown by Sass while evaluating a stylesheet.
 */
public class SassRuntimeException extends SassException {
    private final String sassTrace;

    public SassRuntimeException(String message, FileSpan span) {
        super(message, span);
        this.sassTrace = "";
    }

    public SassRuntimeException(String message, FileSpan span, String sassTrace) {
        super(message, span);
        this.sassTrace = sassTrace;
    }

    public SassRuntimeException(String message, FileSpan span, String sassTrace, Set<URI> loadedUrls) {
        super(message, span, loadedUrls);
        this.sassTrace = sassTrace;
    }

    /** The Sass stack trace at the point this exception was thrown. */
    public String getSassTrace() { return sassTrace; }
}
