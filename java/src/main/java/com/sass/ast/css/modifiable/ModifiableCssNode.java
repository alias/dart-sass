package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssNode;
import org.jspecify.annotations.Nullable;

/**
 * A mutable CSS AST node used during Sass evaluation.
 *
 * <p>During evaluation, the CSS tree is built up using modifiable nodes.
 * After evaluation is complete, the tree is typically serialized into
 * the final CSS output.</p>
 */
public abstract class ModifiableCssNode extends CssNode {

    private @Nullable ModifiableCssParentNode parent;
    private int indexInParent = -1;
    private boolean isGroupEnd;

    protected ModifiableCssNode() {}

    /** Returns the modifiable parent of this node, or null if it has no parent. */
    public @Nullable ModifiableCssParentNode getModifiableParent() {
        return parent;
    }

    /**
     * Sets the parent of this node.
     * Package-private for use by {@link ModifiableCssParentNode}.
     */
    void setParent(@Nullable ModifiableCssParentNode parent) {
        this.parent = parent;
    }

    /** Returns the index of this node in its parent's children list. */
    public int getIndexInParent() {
        return indexInParent;
    }

    /**
     * Sets the index of this node in its parent's children list.
     * Package-private for use by {@link ModifiableCssParentNode}.
     */
    void setIndexInParent(int index) {
        this.indexInParent = index;
    }

    /** Returns whether this node marks the end of a group. */
    @Override
    public boolean isGroupEnd() {
        return isGroupEnd;
    }

    /** Sets whether this node marks the end of a group. */
    @Override
    public void setGroupEnd(boolean groupEnd) {
        this.isGroupEnd = groupEnd;
    }

    /**
     * Removes this node from its parent's children list.
     *
     * @throws IllegalStateException if this node has no parent
     */
    public void remove() {
        if (parent == null) {
            throw new IllegalStateException("Cannot remove a node with no parent.");
        }
        parent.removeChild(this);
        this.parent = null;
        this.indexInParent = -1;
    }
}
