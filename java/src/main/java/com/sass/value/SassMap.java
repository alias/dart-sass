package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A SassScript map (ordered key-value pairs).
 */
public final class SassMap extends Value {
    /** An empty map. */
    public static final SassMap EMPTY = new SassMap(Map.of());

    private final Map<Value, Value> contents;

    /** Creates a map with the given contents. Uses insertion order. */
    public SassMap(Map<Value, Value> contents) {
        this.contents = Collections.unmodifiableMap(new LinkedHashMap<>(contents));
    }

    /** The contents of the map. */
    public Map<Value, Value> getContents() { return contents; }

    @Override
    public ListSeparator getSeparator() {
        return contents.isEmpty() ? ListSeparator.UNDECIDED : ListSeparator.COMMA;
    }

    @Override
    public List<Value> asList() {
        var result = new ArrayList<Value>(contents.size());
        contents.forEach((key, value) ->
                result.add(new SassList(List.of(key, value), ListSeparator.SPACE)));
        return Collections.unmodifiableList(result);
    }

    @Override
    protected int lengthAsList() { return contents.size(); }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitMap(this);
    }

    @Override
    public SassMap assertMap(@Nullable String name) { return this; }

    @Override
    public SassMap tryMap() { return this; }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SassMap o) {
            return contents.equals(o.contents);
        }
        // Empty map equals empty list
        return contents.isEmpty() && other instanceof SassList l && l.asList().isEmpty();
    }

    @Override
    public int hashCode() {
        return contents.isEmpty() ? SassList.EMPTY.hashCode() : contents.hashCode();
    }

    @Override
    public String toString() {
        if (contents.isEmpty()) return "()";
        var sb = new StringBuilder("(");
        boolean first = true;
        for (var entry : contents.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        sb.append(")");
        return sb.toString();
    }
}
