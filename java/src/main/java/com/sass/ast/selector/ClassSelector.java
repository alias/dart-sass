package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.Objects;

/**
 * A class selector, e.g. {@code .foo}.
 */
public final class ClassSelector extends SimpleSelector {

    private final String name;
    private final FileSpan span;

    public ClassSelector(String name, FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the class name (without the leading dot). */
    public String getName() {
        return name;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitClassSelector(this);
    }

    @Override
    public SimpleSelector addSuffix(String suffix) {
        return new ClassSelector(name + suffix, span);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassSelector other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "." + name;
    }
}
