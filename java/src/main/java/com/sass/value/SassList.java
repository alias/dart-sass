package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A SassScript list.
 */
public class SassList extends Value {
    /** An empty comma-separated list with no brackets. */
    public static final SassList EMPTY = new SassList(List.of(), ListSeparator.UNDECIDED, false);

    private final List<Value> contents;
    private final ListSeparator separator;
    private final boolean hasBrackets;

    /** Creates a list with the given contents, separator, and bracket setting. */
    public SassList(List<Value> contents, ListSeparator separator, boolean brackets) {
        if (separator == ListSeparator.UNDECIDED && contents.size() > 1) {
            throw new IllegalArgumentException(
                    "A list with more than one element must have an explicit separator.");
        }
        this.contents = List.copyOf(contents);
        this.separator = separator;
        this.hasBrackets = brackets;
    }

    /** Creates a list without brackets. */
    public SassList(List<Value> contents, ListSeparator separator) {
        this(contents, separator, false);
    }

    @Override
    public ListSeparator getSeparator() { return separator; }

    @Override
    public boolean hasBrackets() { return hasBrackets; }

    @Override
    public List<Value> asList() { return contents; }

    @Override
    protected int lengthAsList() { return contents.size(); }

    @Override
    public boolean isBlank() {
        return !hasBrackets && contents.stream().allMatch(Value::isBlank);
    }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitList(this);
    }

    @Override
    public SassMap assertMap(@Nullable String name) {
        return contents.isEmpty() ? SassMap.EMPTY : super.assertMap(name);
    }

    @Override
    public @Nullable SassMap tryMap() {
        return contents.isEmpty() ? SassMap.EMPTY : null;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SassList o) {
            return separator == o.separator &&
                    hasBrackets == o.hasBrackets &&
                    contents.equals(o.contents);
        }
        // Empty list equals empty map
        return contents.isEmpty() && other instanceof SassMap m && m.getContents().isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents, separator, hasBrackets);
    }

    @Override
    public String toString() {
        if (contents.isEmpty()) {
            return hasBrackets ? "[]" : "()";
        }

        String sep = separator == ListSeparator.COMMA ? ", " :
                     separator == ListSeparator.SLASH ? " / " : " ";

        String inner = contents.stream()
                .map(Value::toString)
                .reduce((a, b) -> a + sep + b)
                .orElse("");

        if (hasBrackets) return "[" + inner + "]";
        if (contents.size() == 1 && separator == ListSeparator.COMMA) {
            return "(" + inner + ",)";
        }
        return "(" + inner + ")";
    }
}
