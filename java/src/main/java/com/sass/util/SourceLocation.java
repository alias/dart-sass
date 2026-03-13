package com.sass.util;

import java.net.URI;

/**
 * A location in a source file, with line, column, and offset tracking.
 * Lines and columns are 0-based.
 */
public record SourceLocation(int offset, int line, int column, SourceFile sourceFile) {

    /** The URI of the source file, if available. */
    public URI sourceUrl() {
        return sourceFile != null ? sourceFile.url() : null;
    }

    /**
     * Returns the distance from this location to {@code other}.
     */
    public int distance(SourceLocation other) {
        return Math.abs(offset - other.offset);
    }

    @Override
    public String toString() {
        var url = sourceUrl();
        var prefix = url != null ? url.toString() + ":" : "";
        return prefix + (line + 1) + ":" + (column + 1);
    }
}
