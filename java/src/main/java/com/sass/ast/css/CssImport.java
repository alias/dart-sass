package com.sass.ast.css;

import com.sass.util.FileSpan;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A plain CSS {@code @import} rule.
 *
 * <p>This represents an {@code @import} that will be emitted as-is in
 * the CSS output (as opposed to Sass imports that are resolved at
 * compile time).</p>
 */
public final class CssImport extends CssNode {

    private final CssValue<String> url;
    private final @Nullable CssValue<String> modifiers;
    private final FileSpan span;

    /**
     * Creates a new CSS import.
     *
     * @param url the URL being imported
     * @param span the source span for the import
     * @param modifiers optional import modifiers (e.g., media queries), or null
     */
    public CssImport(CssValue<String> url, FileSpan span,
                     @Nullable CssValue<String> modifiers) {
        this.url = url;
        this.span = span;
        this.modifiers = modifiers;
    }

    /**
     * Creates a new CSS import without modifiers.
     */
    public CssImport(CssValue<String> url, FileSpan span) {
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
