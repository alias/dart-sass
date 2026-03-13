package com.sass.ast;

import com.sass.util.FileSpan;

/**
 * A node in a Sass abstract syntax tree.
 */
public interface SassNode {
    /** The source span for this node. */
    FileSpan getSpan();
}
