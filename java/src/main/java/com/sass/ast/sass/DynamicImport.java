package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * A dynamic import that loads a Sass or CSS file at runtime.
 */
public final class DynamicImport implements Import {

    private final String urlString;
    private final FileSpan span;

    public DynamicImport(String urlString, FileSpan span) {
        this.urlString = urlString;
        this.span = span;
    }

    public String getUrlString() {
        return urlString;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }
}
