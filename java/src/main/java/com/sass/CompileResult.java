package com.sass;

import org.jspecify.annotations.Nullable;

/**
 * The result of compiling a Sass stylesheet to CSS.
 *
 * @param css the compiled CSS text
 * @param sourceMap the source map JSON, or null if source maps were not requested
 */
public record CompileResult(String css, @Nullable String sourceMap) {
}
