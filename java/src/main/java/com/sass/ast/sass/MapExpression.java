package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.ExpressionVisitor;

import java.util.List;

/**
 * A map literal.
 */
public final class MapExpression extends Expression {

    /** A key-value pair in a map literal. */
    public record ExpressionPair(Expression key, Expression value) {}

    private final List<ExpressionPair> pairs;
    private final FileSpan span;

    public MapExpression(List<ExpressionPair> pairs, FileSpan span) {
        this.pairs = List.copyOf(pairs);
        this.span = span;
    }

    /** The key-value pairs of this map. */
    public List<ExpressionPair> getPairs() {
        return pairs;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitMapExpression(this);
    }
}
