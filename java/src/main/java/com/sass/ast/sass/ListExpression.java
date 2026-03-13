package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.value.ListSeparator;
import com.sass.visitor.ExpressionVisitor;

import java.util.List;

/**
 * A list literal.
 */
public final class ListExpression extends Expression {
    private final List<Expression> contents;
    private final ListSeparator separator;
    private final boolean hasBrackets;
    private final FileSpan span;

    public ListExpression(List<Expression> contents, ListSeparator separator,
                          boolean hasBrackets, FileSpan span) {
        this.contents = List.copyOf(contents);
        this.separator = separator;
        this.hasBrackets = hasBrackets;
        this.span = span;
    }

    public ListExpression(List<Expression> contents, ListSeparator separator, FileSpan span) {
        this(contents, separator, false, span);
    }

    /** The elements of this list. */
    public List<Expression> getContents() {
        return contents;
    }

    /** The separator between elements. */
    public ListSeparator getSeparator() {
        return separator;
    }

    /** Whether this list has square brackets. */
    public boolean hasBrackets() {
        return hasBrackets;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitListExpression(this);
    }
}
