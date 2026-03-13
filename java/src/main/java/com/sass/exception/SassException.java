package com.sass.exception;

import com.sass.util.FileSpan;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * An exception thrown by Sass.
 */
public class SassException extends RuntimeException {
    private final FileSpan span;
    private final Set<URI> loadedUrls;

    public SassException(String message, FileSpan span) {
        this(message, span, Collections.emptySet());
    }

    public SassException(String message, FileSpan span, Set<URI> loadedUrls) {
        super(message);
        this.span = span;
        this.loadedUrls = loadedUrls != null ? Set.copyOf(loadedUrls) : Collections.emptySet();
    }

    /** The source span where the error occurred. */
    public FileSpan getSpan() { return span; }

    /** The set of canonical stylesheet URLs that were loaded before the failure. */
    public Set<URI> getLoadedUrls() { return loadedUrls; }

    /** Returns a copy with a different set of loaded URLs. */
    public SassException withLoadedUrls(Set<URI> loadedUrls) {
        return new SassException(getMessage(), span, loadedUrls);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Error: ").append(getMessage()).append("\n");
        if (span != null) {
            sb.append(span.highlight());
        }
        return sb.toString();
    }
}
