package com.sass.ast.css.modifiable;

import com.sass.ast.css.CssValue;
import com.sass.util.FileSpan;
import com.sass.value.Value;
import com.sass.visitor.CssVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A mutable CSS property declaration used during Sass evaluation.
 */
public final class ModifiableCssDeclaration extends ModifiableCssNode {

    private final CssValue<String> name;
    private final CssValue<Value> value;
    private final @Nullable FileSpan valueSpanForMap;
    private final boolean parsedAsSassScript;
    private final FileSpan span;

    /**
     * Creates a new modifiable CSS declaration.
     *
     * @param name the property name
     * @param value the property value
     * @param span the source span for the declaration
     * @param parsedAsSassScript whether this value was parsed as Sass script
     * @param valueSpanForMap the span to use for source map generation, or null
     */
    public ModifiableCssDeclaration(CssValue<String> name, CssValue<Value> value,
                                    FileSpan span, boolean parsedAsSassScript,
                                    @Nullable FileSpan valueSpanForMap) {
        this.name = name;
        this.value = value;
        this.span = span;
        this.parsedAsSassScript = parsedAsSassScript;
        this.valueSpanForMap = valueSpanForMap;
    }

    /**
     * Creates a new modifiable CSS declaration without a custom value span.
     */
    public ModifiableCssDeclaration(CssValue<String> name, CssValue<Value> value,
                                    FileSpan span, boolean parsedAsSassScript) {
        this(name, value, span, parsedAsSassScript, null);
    }

    /** Returns the property name. */
    public CssValue<String> getName() {
        return name;
    }

    /** Returns the property value. */
    public CssValue<Value> getValue() {
        return value;
    }

    /** Returns the span to use for source map generation, or null. */
    public @Nullable FileSpan getValueSpanForMap() {
        return valueSpanForMap;
    }

    /** Returns whether this value was parsed as Sass script. */
    public boolean isParsedAsSassScript() {
        return parsedAsSassScript;
    }

    /**
     * Returns whether this is a custom property declaration
     * (i.e., the property name starts with "--").
     */
    public boolean isCustomProperty() {
        return name.getValue().startsWith("--");
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(CssVisitor<T> visitor) {
        return visitor.visitCssDeclaration(this);
    }
}
