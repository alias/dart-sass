package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.visitor.StatementVisitor;

/**
 * A statement in a Sass stylesheet.
 */
public abstract class Statement implements SassNode {

    /** Accepts a {@link StatementVisitor} and returns the result. */
    public abstract <T> T accept(StatementVisitor<T> visitor);
}
