package com.sass.ast.css.modifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A mutable CSS parent node used during Sass evaluation.
 *
 * <p>This class provides mutable child management operations. Children
 * are stored in an internal {@link ArrayList} and exposed through an
 * unmodifiable view.</p>
 */
public abstract class ModifiableCssParentNode extends ModifiableCssNode {

    private final ArrayList<ModifiableCssNode> children;
    private final List<ModifiableCssNode> childrenView;

    protected ModifiableCssParentNode() {
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(this.children);
    }

    /** Returns an unmodifiable view of this node's children. */
    public List<ModifiableCssNode> getChildren() {
        return childrenView;
    }

    /**
     * Adds a child to this node.
     *
     * <p>This sets the child's parent reference and index.</p>
     *
     * @param child the child node to add
     */
    public void addChild(ModifiableCssNode child) {
        child.setParent(this);
        child.setIndexInParent(children.size());
        children.add(child);
    }

    /** Removes all children from this node. */
    public void clearChildren() {
        for (var child : children) {
            child.setParent(null);
            child.setIndexInParent(-1);
        }
        children.clear();
    }

    /**
     * Removes a specific child from this node's children list and
     * updates the indices of subsequent children.
     *
     * <p>Package-private; called by {@link ModifiableCssNode#remove()}.</p>
     *
     * @param child the child to remove
     */
    void removeChild(ModifiableCssNode child) {
        int index = child.getIndexInParent();
        children.remove(index);
        // Update indices of subsequent children
        for (int i = index; i < children.size(); i++) {
            children.get(i).setIndexInParent(i);
        }
    }

    /**
     * Creates a shallow copy of this node without any children.
     *
     * @return a new node with the same properties but no children
     */
    public abstract ModifiableCssParentNode copyWithoutChildren();
}
