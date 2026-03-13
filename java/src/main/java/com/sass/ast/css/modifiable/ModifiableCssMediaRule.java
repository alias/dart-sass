package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssMediaQuery;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;

import java.util.Collections;
import java.util.List;

/**
 * A mutable CSS {@code @media} rule used during Sass evaluation.
 */
public final class ModifiableCssMediaRule extends ModifiableCssParentNode {

    private final List<CssMediaQuery> queries;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS media rule.
     *
     * @param queries the media queries for this rule
     * @param span the source span for the rule
     */
    public ModifiableCssMediaRule(List<CssMediaQuery> queries, FileSpan span) {
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

    @Override
    public ModifiableCssMediaRule copyWithoutChildren() {
        return new ModifiableCssMediaRule(queries, span);
    }
}
