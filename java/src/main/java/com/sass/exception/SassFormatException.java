package com.sass.exception;

import com.sass.util.FileSpan;

import java.net.URI;
import java.util.Set;

/**
 * An exception thrown when Sass parsing has failed.
 */
public class SassFormatException extends SassException {

    public SassFormatException(String message, FileSpan span) {
        super(message, span);
    }

    public SassFormatException(String message, FileSpan span, Set<URI> loadedUrls) {
        super(message, span, loadedUrls);
    }
}
