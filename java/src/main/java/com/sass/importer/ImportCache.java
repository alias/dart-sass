package com.sass.importer;

import com.sass.ast.sass.Stylesheet;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches resolved import paths and parsed stylesheets within a
 * single compilation to avoid redundant file I/O and parsing.
 *
 * <p>Ported (simplified) from {@code lib/src/import_cache.dart}.</p>
 */
public final class ImportCache {

    /** Maps canonical path -> parsed Stylesheet. */
    private final Map<Path, Stylesheet> parsedStylesheets = new HashMap<>();

    public @Nullable Stylesheet getParsedStylesheet(Path canonicalPath) {
        return parsedStylesheets.get(canonicalPath);
    }

    public void putParsedStylesheet(Path canonicalPath, Stylesheet stylesheet) {
        parsedStylesheets.put(canonicalPath, stylesheet);
    }
}
