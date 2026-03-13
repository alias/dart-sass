package com.sass.ast.sass;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A statement that can contain child statements (e.g., rules, blocks).
 */
public abstract class ParentStatement extends Statement {

    private final @Nullable List<Statement> children;
    private final boolean hasDeclarations;

    protected ParentStatement(@Nullable List<Statement> children, boolean hasDeclarations) {
        this.children = children != null ? List.copyOf(children) : null;
        this.hasDeclarations = hasDeclarations;
    }

    public @Nullable List<Statement> getChildren() {
        return children;
    }

    public boolean hasDeclarations() {
        return hasDeclarations;
    }
}
