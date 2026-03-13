package com.sass.ast.sass;

import com.sass.util.FileSpan;

/**
 * A condition that selects for browsers where a given declaration is supported.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition/declaration.dart}.
 */
public final class SupportsDeclaration implements SupportsCondition {

    /** The name of the declaration being tested. */
    private final Expression name;

    /** The value of the declaration being tested. */
    private final Expression value;

    private final FileSpan span;

    public SupportsDeclaration(Expression name, Expression value, FileSpan span) {
        this.name = name;
        this.value = value;
        this.span = span;
    }

    /** Returns the name of the declaration being tested. */
    public Expression getName() {
        return name;
    }

    /** Returns the value of the declaration being tested. */
    public Expression getValue() {
        return value;
    }

    /**
     * Returns whether this is a CSS Custom Property declaration.
     *
     * <p>Note that this can return {@code false} for declarations that will
     * ultimately be serialized as custom properties if they aren't <em>parsed
     * as</em> custom properties, such as {@code #{--foo}: ...}.
     *
     * <p>If this returns {@code true}, then {@link #getValue()} will be a
     * {@link StringExpression}.
     */
    public boolean isCustomProperty() {
        if (name instanceof StringExpression strExpr && !strExpr.hasQuotes()) {
            return strExpr.getText().initialPlain().startsWith("--");
        }
        return false;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public SupportsDeclaration withSpan(FileSpan span) {
        return new SupportsDeclaration(name, value, span);
    }

    @Override
    public String toString() {
        return "(" + name + ": " + value + ")";
    }
}
