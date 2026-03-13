package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.Objects;

/**
 * A type selector (element selector), e.g. {@code div} or {@code ns|h1}.
 */
public final class TypeSelector extends SimpleSelector {

    private final QualifiedName name;
    private final FileSpan span;

    public TypeSelector(QualifiedName name, FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the qualified name of this type selector. */
    public QualifiedName getName() {
        return name;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public int getSpecificity() {
        return 1;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitTypeSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeSelector other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
