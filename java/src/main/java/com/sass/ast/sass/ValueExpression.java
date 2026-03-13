package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.value.Value;
import com.sass.visitor.ExpressionVisitor;

/**
 * An expression that directly embeds a runtime {@link Value} in the AST.
 * <p>
 * This is used for dynamically constructed ASTs, not for parsed source code.
 */
public final class ValueExpression extends Expression {
    private final Value value;
    private final FileSpan span;

    public ValueExpression(Value value, FileSpan span) {
        this.value = value;
        this.span = span;
    }

    /** The embedded value. */
    public Value getValue() {
        return value;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitValueExpression(this);
    }
}
