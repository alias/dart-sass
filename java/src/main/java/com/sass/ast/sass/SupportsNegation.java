package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * A negated supports condition.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/negation.dart}.
 */
public final class SupportsNegation implements SupportsCondition {

    /** The condition that's been negated. */
    private final SupportsCondition condition;

    private final FileSpan span;

    public SupportsNegation(SupportsCondition condition, FileSpan span) {
        this.condition = condition;
        this.span = span;
    }

    /** Returns the condition that's been negated. */
    public SupportsCondition getCondition() {
        return condition;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsNegation withSpan(FileSpan span) {
        return new SupportsNegation(condition, span);
    }

    @Override
    public String toString() {
        if (condition instanceof SupportsNegation || condition instanceof SupportsOperation) {
            return "not (" + condition + ")";
        } else {
            return "not " + condition;
        }
    }
}
