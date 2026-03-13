package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * An import in an {@code @import} rule.
 */
public interface Import {
    FileSpan getSpan();
}
