package com.sass.ast.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A CSS node that can have child nodes.
 *
 * <p>This is the base class for all CSS AST nodes that can contain other
 * nodes, such as stylesheets, style rules, and at-rules.</p>
 */
public abstract class CssParentNode extends CssNode {

    private final List<CssNode> children;
    private final List<CssNode> childrenView;

    protected CssParentNode() {
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(this.children);
    }

    /** Returns an unmodifiable view of this node's children. */
    public List<CssNode> getChildren() {
        return childrenView;
    }

    /**
     * Returns the internal mutable children list.
     * Package-private for use by subclasses in the same package.
     */
    List<CssNode> getChildrenInternal() {
        return children;
    }

    /**
     * Returns whether this node has no children and should not allow any.
     * Defaults to false; at-rules may override this.
     */
    public boolean isChildless() {
        return false;
    }
}
