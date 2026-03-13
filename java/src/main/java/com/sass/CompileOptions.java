package com.sass;

import java.nio.file.Path;
import java.util.List;

/**
 * Options for Sass compilation.
 *
 * @param loadPaths directories to search for imported/used files, after relative resolution
 * @param style the CSS output style (EXPANDED or COMPRESSED)
 */
public record CompileOptions(
        List<Path> loadPaths,
        OutputStyle style
) {
    /** Creates options with defaults: no load paths, expanded output. */
    public CompileOptions() {
        this(List.of(), OutputStyle.EXPANDED);
    }

    /** Creates options with load paths and default expanded output. */
    public CompileOptions(List<Path> loadPaths) {
        this(loadPaths, OutputStyle.EXPANDED);
    }

    /** Canonical constructor normalizes the load paths list. */
    public CompileOptions {
        loadPaths = List.copyOf(loadPaths);
    }
}
