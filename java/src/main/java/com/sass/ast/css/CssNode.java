package com.sass.ast.css;

import com.sass.ast.SassNode;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A node in a CSS abstract syntax tree.
 *
 * <p>This is the base class for all CSS AST nodes produced during Sass
 * compilation.</p>
 */
public abstract class CssNode implements SassNode {

    /**
     * The parent node of this node in the CSS tree, or null if this node
     * has no parent (e.g., the root stylesheet).
     */
    private @Nullable CssParentNode parent;

    /**
     * Whether this node marks the end of a group of nodes that should be
     * separated from subsequent nodes with blank lines in the output.
     */
    private boolean isGroupEnd;

    protected CssNode() {}

    /** Returns the parent of this node, or null if it has no parent. */
    public @Nullable CssParentNode getParent() {
        return parent;
    }

    /**
     * Sets the parent of this node.
     * Package-private so that only parent node implementations can set this.
     */
    void setParent(@Nullable CssParentNode parent) {
        this.parent = parent;
    }

    /** Returns whether this node marks the end of a group. */
    public boolean isGroupEnd() {
        return isGroupEnd;
    }

    /** Sets whether this node marks the end of a group. */
    public void setGroupEnd(boolean groupEnd) {
        this.isGroupEnd = groupEnd;
    }

    /**
     * Calls the appropriate visit method on {@code visitor}.
     *
     * @param visitor the CSS visitor
     * @param <T> the return type of the visitor
     * @return the result of the visit method
     */
    public abstract <T> T accept(CssVisitor<T> visitor);

    /**
     * Returns whether this node is invisible and should not be emitted in
     * the output CSS. Defaults to false.
     */
    public boolean isInvisible() {
        return false;
    }
}
