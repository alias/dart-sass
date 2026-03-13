package com.sass.ast.css;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A plain CSS media query, as used in {@code @media} rules.
 *
 * <p>This is not a CSS AST node; it is a data holder used by
 * {@link CssMediaRule}.</p>
 */
public final class CssMediaQuery {

    /** The modifier, such as "not" or "only", or null if absent. */
    private final @Nullable String modifier;

    /** The media type, such as "screen" or "print", or null if absent. */
    private final @Nullable String type;

    /** Whether conditions are joined with AND (true) or OR (false). */
    private final boolean conjunction;

    /** The query conditions (e.g., "(min-width: 800px)"). */
    private final List<String> conditions;

    /**
     * Creates a media query with the given components.
     *
     * @param modifier the modifier ("not", "only"), or null
     * @param type the media type, or null
     * @param conditions the list of conditions
     * @param conjunction true for AND, false for OR
     */
    public CssMediaQuery(
            @Nullable String modifier,
            @Nullable String type,
            List<String> conditions,
            boolean conjunction) {
        this.modifier = modifier;
        this.type = type;
        this.conditions = Collections.unmodifiableList(List.copyOf(conditions));
        this.conjunction = conjunction;
    }

    /**
     * Creates a media query with conditions only and AND conjunction.
     */
    public CssMediaQuery(List<String> conditions) {
        this(null, null, conditions, true);
    }

    /** Returns the modifier ("not" or "only"), or null. */
    public @Nullable String getModifier() {
        return modifier;
    }

    /** Returns the media type, or null. */
    public @Nullable String getType() {
        return type;
    }

    /** Returns whether conditions are joined with AND (true) or OR (false). */
    public boolean isConjunction() {
        return conjunction;
    }

    /** Returns an unmodifiable list of conditions. */
    public List<String> getConditions() {
        return conditions;
    }

    /**
     * Returns whether this query matches all media types.
     * This is true when there is no explicit type specified.
     */
    public boolean matchesAllTypes() {
        return type == null || type.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CssMediaQuery other)) return false;
        return conjunction == other.conjunction
                && Objects.equals(modifier, other.modifier)
                && Objects.equals(type, other.type)
                && conditions.equals(other.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifier, type, conjunction, conditions);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if (modifier != null) {
            sb.append(modifier).append(' ');
        }
        if (type != null) {
            sb.append(type);
            if (!conditions.isEmpty()) {
                sb.append(" and ");
            }
        }
        sb.append(String.join(conjunction ? " and " : " or ", conditions));
        return sb.toString();
    }
}
