package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An attribute selector, e.g. {@code [href]}, {@code [type="text"]}, or
 * {@code [class~="active" i]}.
 */
public final class AttributeSelector extends SimpleSelector {

    private final QualifiedName name;
    private final @Nullable AttributeOperator op;
    private final @Nullable String value;
    private final @Nullable String modifier;
    private final FileSpan span;

    /**
     * Creates an attribute selector with all fields.
     *
     * @param name the attribute name
     * @param op the comparison operator, or null for presence-only selectors
     * @param value the value to compare against, or null for presence-only selectors
     * @param modifier the case sensitivity modifier (e.g. "i"), or null
     * @param span the source span
     */
    public AttributeSelector(QualifiedName name, @Nullable AttributeOperator op,
                             @Nullable String value, @Nullable String modifier,
                             FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.op = op;
        this.value = value;
        this.modifier = modifier;
        this.span = Objects.requireNonNull(span);
    }

    /**
     * Creates a presence-only attribute selector (e.g. {@code [href]}).
     *
     * @param name the attribute name
     * @param span the source span
     */
    public AttributeSelector(QualifiedName name, FileSpan span) {
        this(name, null, null, null, span);
    }

    /** Returns the qualified name of the attribute. */
    public QualifiedName getName() {
        return name;
    }

    /** Returns the comparison operator, or null for presence-only selectors. */
    public @Nullable AttributeOperator getOp() {
        return op;
    }

    /** Returns the value to compare against, or null for presence-only selectors. */
    public @Nullable String getValue() {
        return value;
    }

    /** Returns the case sensitivity modifier (e.g. "i"), or null. */
    public @Nullable String getModifier() {
        return modifier;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitAttributeSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeSelector other)) return false;
        return name.equals(other.name)
                && Objects.equals(op, other.op)
                && Objects.equals(value, other.value)
                && Objects.equals(modifier, other.modifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, op, value, modifier);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append('[').append(name);
        if (op != null) {
            sb.append(op);
            if (value != null) {
                sb.append('"').append(value).append('"');
            }
            if (modifier != null) {
                sb.append(' ').append(modifier);
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
