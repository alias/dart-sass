package com.sass.ast.selector;

import org.jspecify.annotations.Nullable;

/**
 * A qualified name consisting of an optional namespace and a name.
 *
 * <p>The namespace has the following semantics:
 * <ul>
 *   <li>{@code null} — the default namespace (no namespace specified)</li>
 *   <li>{@code ""} — explicitly no namespace (written as {@code |name})</li>
 *   <li>{@code "*"} — any namespace (written as {@code *|name})</li>
 *   <li>any other value — a specific namespace prefix</li>
 * </ul>
 *
 * @param name the local name
 * @param namespace the namespace prefix, or null for default namespace
 */
public record QualifiedName(String name, @Nullable String namespace) {

    /**
     * Creates a qualified name with only a local name and no namespace.
     *
     * @param name the local name
     */
    public QualifiedName(String name) {
        this(name, null);
    }

    @Override
    public String toString() {
        if (namespace == null) {
            return name;
        }
        return namespace + "|" + name;
    }
}
