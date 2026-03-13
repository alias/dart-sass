package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;

import java.util.List;
import java.util.Objects;

/**
 * An ID selector, e.g. {@code #foo}.
 */
public final class IDSelector extends SimpleSelector {

    private final String name;
    private final FileSpan span;

    public IDSelector(String name, FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the ID name (without the leading hash). */
    public String getName() {
        return name;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public int getSpecificity() {
        return 1000000;
    }

    @Override
    public SimpleSelector addSuffix(String suffix) {
        return new IDSelector(name + suffix, span);
    }

    @Override
    public List<SimpleSelector> unify(List<SimpleSelector> compound) {
        // An element can only have one ID. If the compound already has a
        // different ID, unification is impossible.
        for (SimpleSelector selector : compound) {
            if (selector instanceof IDSelector other && !other.name.equals(name)) {
                return null;
            }
        }
        return super.unify(compound);
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitIDSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IDSelector other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "#" + name;
    }
}
