package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

import java.util.Collections;
import java.util.List;

/**
 * A plain CSS {@code @media} rule.
 */
public final class CssMediaRule extends CssParentNode {

    private final List<CssMediaQuery> queries;
    private final FileSpan span;

    /**
     * Creates a new CSS media rule.
     *
     * @param queries the media queries for this rule
     * @param span the source span for the rule
     */
    public CssMediaRule(List<CssMediaQuery> queries, FileSpan span) {
        this.queries = Collections.unmodifiableList(List.copyOf(queries));
        this.span = span;
    }

    /** Returns an unmodifiable list of media queries. */
    public List<CssMediaQuery> getQueries() {
        return queries;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssMediaRule(this);
    }
}
