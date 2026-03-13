package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

/**
 * A node in a Sass expression tree.
 */
public abstract class Expression implements SassNode {

    protected Expression() {}

    /** Calls the appropriate visit method on {@code visitor}. */
    public abstract <T> T accept(ExpressionVisitor<T> visitor);

    /**
     * Whether this expression can be used in a calculation context without
     * being parenthesized.
     */
    public boolean isCalculationSafe() {
        return false;
    }
}
