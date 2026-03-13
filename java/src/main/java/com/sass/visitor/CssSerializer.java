package com.sass.visitor;

import com.sass.OutputStyle;
import com.sass.ast.css.CssNode;
import com.sass.ast.selector.Selector;
import com.sass.value.Value;

/**
 * Public API for serializing CSS AST nodes, Sass values, and selectors
 * into CSS text.
 *
 * <p>This is the Java port of dart-sass's top-level {@code serialize},
 * {@code serializeValue}, and {@code serializeSelector} functions.</p>
 */
public final class CssSerializer {

    private CssSerializer() {}

    /**
     * Serializes a CSS AST node to CSS text.
     *
     * @param node  the CSS AST node to serialize
     * @param style the output style (EXPANDED or COMPRESSED)
     * @return the serialized CSS text
     */
    public static String serialize(CssNode node, OutputStyle style) {
        var visitor = new SerializeVisitor(style, false, true);
        node.accept(visitor);
        return visitor.getResult();
    }

    /**
     * Serializes a CSS AST node to CSS text with expanded style.
     *
     * @param node the CSS AST node to serialize
     * @return the serialized CSS text
     */
    public static String serialize(CssNode node) {
        return serialize(node, OutputStyle.EXPANDED);
    }

    /**
     * Serializes a Sass value to its CSS representation.
     *
     * <p>If {@code inspect} is true, this will emit an unambiguous
     * representation of the source structure. Note that while this will
     * be valid SCSS, it may not be valid CSS.</p>
     *
     * <p>If {@code quote} is false, quoted strings are emitted without
     * quotes.</p>
     *
     * @param value   the value to serialize
     * @param inspect whether to emit an unambiguous representation
     * @param quote   whether to quote strings
     * @return the serialized value text
     */
    public static String serializeValue(Value value, boolean inspect, boolean quote) {
        var visitor = new SerializeVisitor(null, inspect, quote);
        value.accept(visitor);
        return visitor.getResult();
    }

    /**
     * Serializes a Sass value to its CSS representation with default settings.
     *
     * @param value the value to serialize
     * @return the serialized value text
     */
    public static String serializeValue(Value value) {
        return serializeValue(value, false, true);
    }

    /**
     * Serializes a selector to its CSS representation.
     *
     * <p>If {@code inspect} is true, this will emit an unambiguous
     * representation of the source structure.</p>
     *
     * @param selector the selector to serialize
     * @param inspect  whether to emit an unambiguous representation
     * @return the serialized selector text
     */
    public static String serializeSelector(Selector selector, boolean inspect) {
        var visitor = new SerializeVisitor(null, inspect, true);
        selector.accept(visitor);
        return visitor.getResult();
    }

    /**
     * Serializes a selector to its CSS representation.
     *
     * @param selector the selector to serialize
     * @return the serialized selector text
     */
    public static String serializeSelector(Selector selector) {
        return serializeSelector(selector, false);
    }
}
