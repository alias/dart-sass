package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A mutable CSS {@code @import} rule used during Sass evaluation.
 */
public final class ModifiableCssImport extends ModifiableCssNode {

    private final CssValue<String> url;
    private final @Nullable CssValue<String> modifiers;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS import.
     *
     * @param url the URL being imported
     * @param span the source span for the import
     * @param modifiers optional import modifiers, or null
     */
    public ModifiableCssImport(CssValue<String> url, FileSpan span,
                               @Nullable CssValue<String> modifiers) {
        this.url = url;
        this.span = span;
        this.modifiers = modifiers;
    }

    /**
     * Creates a new modifiable CSS import without modifiers.
     */
    public ModifiableCssImport(CssValue<String> url, FileSpan span) {
        this(url, span, null);
    }

    /** Returns the imported URL. */
    public CssValue<String> getUrl() {
        return url;
    }

    /** Returns the import modifiers, or null if none. */
    public @Nullable CssValue<String> getModifiers() {
        return modifiers;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssImport(this);
    }
}
