package com.sass.visitor;

import com.sass.OutputStyle;
import com.sass.ast.css.*;
import com.sass.ast.css.modifiable.*;
import com.sass.ast.selector.*;
import com.sass.exception.SassScriptException;
import com.sass.util.FuzzyMath;
import com.sass.value.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * A visitor that converts CSS AST nodes, Sass values, and selectors into
 * plain CSS text.
 *
 * <p>This is the Java port of dart-sass's {@code _SerializeVisitor}. It
 * implements three visitor interfaces so it can serialize any combination
 * of CSS nodes, values, and selectors into a single output buffer.</p>
 */
public final class SerializeVisitor
        implements CssVisitor<Void>, ValueVisitor<Void>, SelectorVisitor<Void> {

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    /** The output buffer that accumulates the serialized CSS. */
    private final StringBuilder buffer = new StringBuilder();

    /** The current indentation level (number of indent units). */
    private int indentation = 0;

    /** The output style (EXPANDED or COMPRESSED). */
    private final OutputStyle style;

    /**
     * Whether to emit an unambiguous representation of the source structure
     * (inspect mode). When true, the output is valid SCSS but may not be
     * valid CSS.
     */
    private final boolean inspect;

    /** Whether quoted strings should be emitted with quotes. */
    private final boolean quote;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    /**
     * Creates a new serialize visitor.
     *
     * @param style   the output style
     * @param inspect whether to emit an unambiguous representation
     * @param quote   whether to quote strings that have quotes
     */
    public SerializeVisitor(OutputStyle style, boolean inspect, boolean quote) {
        this.style = style != null ? style : OutputStyle.EXPANDED;
        this.inspect = inspect;
        this.quote = quote;
    }

    /** Creates a visitor with default settings (expanded, no inspect, quoting on). */
    public SerializeVisitor() {
        this(OutputStyle.EXPANDED, false, true);
    }

    // ---------------------------------------------------------------
    // Public accessors
    // ---------------------------------------------------------------

    /** Returns the serialized CSS text accumulated so far. */
    public String getResult() {
        return buffer.toString();
    }

    // ---------------------------------------------------------------
    // CSS Node Visit Methods
    // ---------------------------------------------------------------

    @Override
    public Void visitCssStylesheet(CssNode node) {
        var children = getChildren(node);
        CssNode previous = null;
        for (var child : children) {
            if (isInvisible(child)) continue;

            if (previous != null) {
                if (requiresSemicolon(previous)) buffer.append(';');
                if (isTrailingComment(child, previous)) {
                    writeOptionalSpace();
                } else {
                    writeLineFeed();
                    if (previous.isGroupEnd()) writeLineFeed();
                }
            }
            previous = child;
            child.accept(this);
        }

        if (previous != null && requiresSemicolon(previous) && !isCompressed()) {
            buffer.append(';');
        }
        return null;
    }

    @Override
    public Void visitCssComment(CssNode node) {
        String text;
        boolean preserved;
        if (node instanceof CssComment comment) {
            text = comment.getText();
            preserved = comment.isPreserved();
        } else {
            var comment = (ModifiableCssComment) node;
            text = comment.getText();
            preserved = comment.isPreserved();
        }

        if (isCompressed() && !preserved) return null;
        if (text.startsWith("/*# source")) return null;

        Integer minimumIndentation = minimumIndentation(text);
        if (minimumIndentation != null && minimumIndentation != -1) {
            minimumIndentation = Math.min(minimumIndentation,
                    node.getSpan().start().column());
            writeIndentation();
            writeWithIndent(text, minimumIndentation);
        } else {
            writeIndentation();
            buffer.append(text);
        }
        return null;
    }

    @Override
    public Void visitCssAtRule(CssNode node) {
        String name;
        String value = null;
        boolean childless;

        if (node instanceof CssAtRule atRule) {
            name = atRule.getName().getValue();
            value = atRule.getValue() != null ? atRule.getValue().getValue() : null;
            childless = atRule.isChildless();
        } else {
            var atRule = (ModifiableCssAtRule) node;
            name = atRule.getName().getValue();
            value = atRule.getValue() != null ? atRule.getValue().getValue() : null;
            childless = atRule.isChildless();
        }

        writeIndentation();
        buffer.append('@');
        buffer.append(name);

        if (value != null) {
            buffer.append(' ');
            buffer.append(value);
        }

        if (!childless) {
            writeOptionalSpace();
            visitChildren(node);
        }
        return null;
    }

    @Override
    public Void visitCssMediaRule(CssNode node) {
        List<CssMediaQuery> queries;
        if (node instanceof CssMediaRule mediaRule) {
            queries = mediaRule.getQueries();
        } else {
            queries = ((ModifiableCssMediaRule) node).getQueries();
        }

        writeIndentation();
        buffer.append("@media");
        var firstQuery = queries.get(0);
        // In compressed mode, the space after @media can only be omitted when the
        // query starts with '(' (e.g. "@media(min-width:800px)"). In all other cases
        // — modifier ("only", "not"), media type ("screen", "print"), or conditions
        // stored as unparsed text — the space is mandatory.
        var firstCondition = firstQuery.getConditions().isEmpty()
                ? null : firstQuery.getConditions().get(0);
        if (!isCompressed()
                || firstQuery.getModifier() != null
                || firstQuery.getType() != null
                || (firstCondition != null && !firstCondition.startsWith("("))) {
            buffer.append(' ');
        }

        writeBetween(queries, commaSeparator(), this::visitMediaQuery);

        writeOptionalSpace();
        visitChildren(node);
        return null;
    }

    @Override
    public Void visitCssImport(CssNode node) {
        String url;
        String modifiers = null;
        if (node instanceof CssImport importNode) {
            url = importNode.getUrl().getValue();
            modifiers = importNode.getModifiers() != null ? importNode.getModifiers().getValue() : null;
        } else {
            var importNode = (ModifiableCssImport) node;
            url = importNode.getUrl().getValue();
            modifiers = importNode.getModifiers() != null ? importNode.getModifiers().getValue() : null;
        }

        writeIndentation();
        buffer.append("@import");
        writeOptionalSpace();
        writeImportUrl(url);

        if (modifiers != null) {
            writeOptionalSpace();
            buffer.append(modifiers);
        }
        return null;
    }

    @Override
    public Void visitCssKeyframeBlock(CssNode node) {
        List<String> selectorValues;
        if (node instanceof CssKeyframeBlock keyframeBlock) {
            selectorValues = keyframeBlock.getSelector().getValue();
        } else {
            selectorValues = ((ModifiableCssKeyframeBlock) node).getSelector().getValue();
        }

        writeIndentation();
        writeBetween(selectorValues, commaSeparator(), s -> buffer.append(s));

        writeOptionalSpace();
        visitChildren(node);
        return null;
    }

    @Override
    public Void visitCssStyleRule(CssNode node) {
        String selector;
        if (node instanceof CssStyleRule styleRule) {
            selector = styleRule.getSelector().getValue();
        } else {
            selector = ((ModifiableCssStyleRule) node).getSelector().getValue();
        }

        writeIndentation();
        if (isCompressed()) {
            buffer.append(selector);
        } else {
            // In expanded mode, split comma-separated selectors onto separate lines
            writeMultiLineSelector(selector);
        }
        writeOptionalSpace();
        visitChildren(node);
        return null;
    }

    /**
     * Writes a comma-separated selector list with each selector on its own line,
     * matching dart-sass's expanded output format.
     * Commas inside parentheses (e.g. {@code :is(.a, .b)}) are not split on.
     */
    private void writeMultiLineSelector(String selector) {
        int depth = 0;
        int start = 0;
        boolean first = true;
        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                if (!first) writeIndentation();
                first = false;
                buffer.append(selector, start, i + 1); // include comma
                buffer.append('\n');
                start = i + 1;
                // Skip all whitespace (including newlines) after comma
                while (start < selector.length()
                        && (selector.charAt(start) == ' '
                        || selector.charAt(start) == '\n'
                        || selector.charAt(start) == '\r'
                        || selector.charAt(start) == '\t')) {
                    start++;
                }
            }
        }
        // Write the last (or only) selector
        if (!first) writeIndentation();
        if (start < selector.length()) {
            buffer.append(selector, start, selector.length());
        }
    }

    @Override
    public Void visitCssSupportsRule(CssNode node) {
        String conditionValue;
        if (node instanceof CssSupportsRule supportsRule) {
            conditionValue = supportsRule.getCondition().getValue();
        } else {
            conditionValue = ((ModifiableCssSupportsRule) node).getCondition().getValue();
        }

        writeIndentation();
        buffer.append("@supports");
        if (!(isCompressed() && !conditionValue.isEmpty()
                && conditionValue.charAt(0) == '(')) {
            buffer.append(' ');
        }

        buffer.append(conditionValue);

        writeOptionalSpace();
        visitChildren(node);
        return null;
    }

    @Override
    public Void visitCssDeclaration(CssNode node) {
        String name;
        Value value;
        boolean parsedAsSassScript;

        if (node instanceof CssDeclaration declaration) {
            name = declaration.getName().getValue();
            value = declaration.getValue().getValue();
            parsedAsSassScript = declaration.isParsedAsSassScript();
        } else {
            var declaration = (ModifiableCssDeclaration) node;
            name = declaration.getName().getValue();
            value = declaration.getValue().getValue();
            parsedAsSassScript = declaration.isParsedAsSassScript();
        }

        writeIndentation();
        buffer.append(name);
        buffer.append(':');

        if (!parsedAsSassScript) {
            if (value instanceof SassString s) {
                if (isCompressed()) {
                    writeFoldedValue(s.getText());
                } else {
                    buffer.append(' ');
                    buffer.append(s.getText());
                }
            } else {
                writeOptionalSpace();
                value.accept(this);
            }
        } else {
            writeOptionalSpace();
            value.accept(this);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Media Query Helpers
    // ---------------------------------------------------------------

    /** Writes a single media query. */
    private void visitMediaQuery(CssMediaQuery query) {
        if (query.getModifier() != null) {
            buffer.append(query.getModifier());
            buffer.append(' ');
        }

        if (query.getType() != null) {
            buffer.append(query.getType());
            if (!query.getConditions().isEmpty()) {
                buffer.append(" and ");
            }
        }

        var conditions = query.getConditions();
        if (conditions.size() == 1 && conditions.get(0).startsWith("(not ")) {
            buffer.append("not ");
            var condition = conditions.get(0);
            buffer.append(condition, "(not ".length(), condition.length() - 1);
        } else {
            var operator = query.isConjunction() ? "and" : "or";
            writeBetween(conditions,
                    isCompressed() ? operator + " " : " " + operator + " ",
                    s -> buffer.append(s));
        }
    }

    // ---------------------------------------------------------------
    // Import URL Helper
    // ---------------------------------------------------------------

    /** Writes an import URL, optimizing in compressed mode. */
    private void writeImportUrl(String url) {
        if (!isCompressed() || url.isEmpty() || url.charAt(0) != 'u') {
            buffer.append(url);
            return;
        }

        // If this is url(...), remove the surrounding function for terser output.
        var urlContents = url.substring(4, url.length() - 1);
        if (!urlContents.isEmpty()) {
            char maybeQuote = urlContents.charAt(0);
            if (maybeQuote == '\'' || maybeQuote == '"') {
                buffer.append(urlContents);
            } else {
                visitQuotedString(urlContents, false);
            }
        }
    }

    // ---------------------------------------------------------------
    // Custom Property Value Helpers
    // ---------------------------------------------------------------

    /**
     * Emits a custom property value with all newlines followed by whitespace
     * collapsed to a single space (compressed mode).
     */
    private void writeFoldedValue(String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                buffer.append(' ');
                i++;
                while (i < text.length() && isWhitespace(text.charAt(i))) {
                    i++;
                }
            } else {
                buffer.append(c);
                i++;
            }
        }
    }

    /**
     * Emits a custom property value, re-indented relative to the current
     * indentation (expanded mode).
     */
    private void writeReindentedValue(String text, CssDeclaration declaration) {
        Integer minIndent = minimumIndentation(text);
        if (minIndent == null) {
            buffer.append(text);
        } else if (minIndent == -1) {
            buffer.append(trimAsciiRight(text));
            buffer.append(' ');
        } else {
            int nameCol = declaration.getName().getSpan().start().column();
            writeWithIndent(text, Math.min(minIndent, nameCol));
        }
    }

    // ---------------------------------------------------------------
    // Value Visit Methods
    // ---------------------------------------------------------------

    @Override
    public Void visitBoolean(SassBoolean value) {
        buffer.append(value.getValue() ? "true" : "false");
        return null;
    }

    @Override
    public Void visitNull() {
        if (inspect) buffer.append("null");
        return null;
    }

    @Override
    public Void visitNumber(SassNumber value) {
        var asSlash = value.getAsSlash();
        if (asSlash != null) {
            visitNumber(asSlash[0]);
            buffer.append('/');
            visitNumber(asSlash[1]);
            return null;
        }

        if (!Double.isFinite(value.getValue())) {
            visitCalculation(SassCalculation.calc(value));
            return null;
        }

        if (value.hasComplexUnits()) {
            visitCalculation(SassCalculation.calc(value));
        } else {
            writeNumber(value.getValue());
            var numerators = value.getNumeratorUnits();
            if (numerators.size() == 1 && value.getDenominatorUnits().isEmpty()) {
                buffer.append(numerators.get(0));
            }
        }
        return null;
    }

    @Override
    public Void visitString(SassString string) {
        if (quote && string.hasQuotes()) {
            visitQuotedString(string.getText(), false);
        } else {
            visitUnquotedString(string.getText());
        }
        return null;
    }

    @Override
    public Void visitColor(SassColor value) {
        // This Java port targets the legacy RGB color space only.
        writeLegacyColor(value);
        return null;
    }

    @Override
    public Void visitList(SassList value) {
        if (value.hasBrackets()) {
            buffer.append('[');
        } else if (value.asList().isEmpty()) {
            if (!inspect) {
                throw new SassScriptException("() isn't a valid CSS value.");
            }
            buffer.append("()");
            return null;
        }

        boolean singleton = inspect
                && value.asList().size() == 1
                && (value.getSeparator() == ListSeparator.COMMA
                    || value.getSeparator() == ListSeparator.SLASH);
        if (singleton && !value.hasBrackets()) buffer.append('(');

        Iterable<Value> elements = inspect
                ? value.asList()
                : value.asList().stream().filter(e -> !e.isBlank()).toList();

        String sep = separatorString(value.getSeparator());

        if (inspect) {
            writeBetween(elements, sep, element -> {
                boolean needsParens = elementNeedsParens(value.getSeparator(), element);
                if (needsParens) buffer.append('(');
                element.accept(this);
                if (needsParens) buffer.append(')');
            });
        } else {
            writeBetween(elements, sep, element -> element.accept(this));
        }

        if (singleton) {
            buffer.append(value.getSeparator().getSeparator());
            if (!value.hasBrackets()) buffer.append(')');
        }

        if (value.hasBrackets()) buffer.append(']');
        return null;
    }

    @Override
    public Void visitMap(SassMap map) {
        if (!inspect) {
            throw new SassScriptException(map + " isn't a valid CSS value.");
        }
        buffer.append('(');
        writeBetween(map.getContents().entrySet(), ", ",
                (Map.Entry<Value, Value> entry) -> {
                    writeMapElement(entry.getKey());
                    buffer.append(": ");
                    writeMapElement(entry.getValue());
                });
        buffer.append(')');
        return null;
    }

    @Override
    public Void visitFunction(SassFunction function) {
        if (!inspect) {
            throw new SassScriptException(function + " isn't a valid CSS value.");
        }
        buffer.append("get-function(");
        visitQuotedString(function.getCallable().toString(), false);
        buffer.append(')');
        return null;
    }

    @Override
    public Void visitMixin(SassMixin mixin) {
        if (!inspect) {
            throw new SassScriptException(mixin + " isn't a valid CSS value.");
        }
        buffer.append("get-mixin(");
        visitQuotedString(mixin.getCallable().toString(), false);
        buffer.append(')');
        return null;
    }

    @Override
    public Void visitCalculation(SassCalculation value) {
        buffer.append(value.getName());
        buffer.append('(');
        writeBetween(value.getArguments(), commaSeparator(),
                this::writeCalculationValue);
        buffer.append(')');
        return null;
    }

    // ---------------------------------------------------------------
    // Calculation Helpers
    // ---------------------------------------------------------------

    /** Writes a single calculation argument value. */
    private void writeCalculationValue(Object value) {
        if (value instanceof SassNumber num) {
            if (!Double.isFinite(num.getValue())) {
                if (num.getValue() == Double.POSITIVE_INFINITY) {
                    buffer.append("infinity");
                } else if (num.getValue() == Double.NEGATIVE_INFINITY) {
                    buffer.append("-infinity");
                } else {
                    buffer.append("NaN");
                }
                writeCalculationUnits(num.getNumeratorUnits(), num.getDenominatorUnits());
                return;
            }

            if (num.hasComplexUnits()) {
                writeNumber(num.getValue());
                var numerators = num.getNumeratorUnits();
                if (!numerators.isEmpty()) {
                    buffer.append(numerators.get(0));
                    writeCalculationUnits(
                            numerators.subList(1, numerators.size()),
                            num.getDenominatorUnits());
                } else {
                    writeCalculationUnits(List.of(), num.getDenominatorUnits());
                }
                return;
            }
        }

        if (value instanceof Value v) {
            v.accept(this);
            return;
        }

        if (value instanceof SassCalculation.CalculationOperation op) {
            boolean parenthesizeLeft = op.left() instanceof SassCalculation.CalculationOperation leftOp
                    && operatorPrecedence(leftOp.operator()) < operatorPrecedence(op.operator());
            if (parenthesizeLeft) buffer.append('(');
            writeCalculationValue(op.left());
            if (parenthesizeLeft) buffer.append(')');

            boolean operatorWhitespace = !isCompressed() || operatorPrecedence(op.operator()) == 1;
            if (operatorWhitespace) buffer.append(' ');
            buffer.append(op.operator());
            if (operatorWhitespace) buffer.append(' ');

            boolean parenthesizeRight =
                    (op.right() instanceof SassCalculation.CalculationOperation rightOp
                            && parenthesizeCalculationRhs(op.operator(), rightOp.operator()))
                    || (op.operator().equals("/")
                        && op.right() instanceof SassNumber rn
                        && (Double.isFinite(rn.getValue())
                            ? rn.hasComplexUnits()
                            : rn.hasUnits()));
            if (parenthesizeRight) buffer.append('(');
            writeCalculationValue(op.right());
            if (parenthesizeRight) buffer.append(')');
            return;
        }

        if (value instanceof SassCalculation.CalculationInterpolation interp) {
            buffer.append(interp.value());
            return;
        }

        // Fallback: try toString
        buffer.append(value);
    }

    /**
     * Writes complex numerator and denominator units beyond the first
     * numerator unit for a number as they appear in a calculation.
     */
    private void writeCalculationUnits(List<String> numeratorUnits, List<String> denominatorUnits) {
        for (String unit : numeratorUnits) {
            writeOptionalSpace();
            buffer.append('*');
            writeOptionalSpace();
            buffer.append('1');
            buffer.append(unit);
        }
        for (String unit : denominatorUnits) {
            writeOptionalSpace();
            buffer.append('/');
            writeOptionalSpace();
            buffer.append('1');
            buffer.append(unit);
        }
    }

    /** Returns the precedence of a calculation operator. */
    private int operatorPrecedence(String operator) {
        return switch (operator) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }

    /**
     * Returns whether the right-hand operation of a calculation should be
     * parenthesized. {@code outer} is the outer operator and {@code right}
     * is the inner right-hand operator.
     */
    private boolean parenthesizeCalculationRhs(String outer, String right) {
        if (outer.equals("/")) return true;
        if (outer.equals("+")) return false;
        // For * or -: parenthesize if right is + or -
        return right.equals("+") || right.equals("-");
    }

    // ---------------------------------------------------------------
    // Color Serialization
    // ---------------------------------------------------------------

    /**
     * Writes a legacy (RGB/HSL/HWB) color in the shortest compatible
     * representation.
     */
    private void writeLegacyColor(SassColor color) {
        boolean opaque = FuzzyMath.fuzzyEquals(color.getAlpha(), 1.0);

        // In compressed mode, emit colors in the shortest representation.
        if (isCompressed()) {
            if (opaque && tryIntegerRgb(color)) return;

            String red = writeNumberToString(color.getRed());
            String green = writeNumberToString(color.getGreen());
            String blue = writeNumberToString(color.getBlue());

            String hue = writeNumberToString(color.getHue());
            String saturation = writeNumberToString(color.getSaturation());
            String lightness = writeNumberToString(color.getLightness());

            // Add two characters for HSL for the %s on saturation and lightness.
            if (red.length() + green.length() + blue.length()
                    <= hue.length() + saturation.length() + lightness.length() + 2) {
                buffer.append(opaque ? "rgb(" : "rgba(");
                buffer.append(red);
                buffer.append(',');
                buffer.append(green);
                buffer.append(',');
                buffer.append(blue);
            } else {
                buffer.append(opaque ? "hsl(" : "hsla(");
                buffer.append(hue);
                buffer.append(',');
                buffer.append(saturation);
                buffer.append("%,");
                buffer.append(lightness);
                buffer.append('%');
            }
            if (!opaque) {
                buffer.append(',');
                writeNumber(color.getAlpha());
            }
            buffer.append(')');
            return;
        }

        // If the color has a preserved original format (from the source),
        // use it in expanded mode — this matches dart-sass behavior where
        // #ffffff stays #ffffff, #fff stays #fff, white stays white.
        if (opaque) {
            var format = color.getFormat();
            if (format != null) {
                buffer.append(format);
                return;
            }

            // For computed colors with integer RGB values, use hex.
            if (canUseHex(color)) {
                buffer.append('#');
                writeHexComponent((int) Math.round(color.getRed()));
                writeHexComponent((int) Math.round(color.getGreen()));
                writeHexComponent((int) Math.round(color.getBlue()));
                return;
            }
        }

        writeRgb(color);
    }

    /**
     * If the color can be written as a hex code or color name, writes it
     * in the shortest format possible and returns true. Otherwise, returns
     * false without writing anything.
     */
    private boolean tryIntegerRgb(SassColor color) {
        if (!canUseHex(color)) return false;

        int redInt = (int) Math.round(color.getRed());
        int greenInt = (int) Math.round(color.getGreen());
        int blueInt = (int) Math.round(color.getBlue());

        boolean shortHex = canUseShortHex(redInt, greenInt, blueInt);
        var name = ColorNames.COLORS_TO_NAMES.get(color);
        if (name != null && name.length() <= (shortHex ? 4 : 7)) {
            buffer.append(name);
        } else if (shortHex) {
            buffer.append('#');
            buffer.append(hexCharFor(redInt & 0xF));
            buffer.append(hexCharFor(greenInt & 0xF));
            buffer.append(hexCharFor(blueInt & 0xF));
        } else {
            buffer.append('#');
            writeHexComponent(redInt);
            writeHexComponent(greenInt);
            writeHexComponent(blueInt);
        }
        return true;
    }

    /** Whether the color can be represented as hex. */
    private boolean canUseHex(SassColor color) {
        return canUseHexForChannel(color.getRed())
                && canUseHexForChannel(color.getGreen())
                && canUseHexForChannel(color.getBlue());
    }

    /** Whether a channel value can be represented as a two-character hex value. */
    private boolean canUseHexForChannel(double channel) {
        return FuzzyMath.fuzzyIsInt(channel)
                && FuzzyMath.fuzzyGreaterThanOrEquals(channel, 0)
                && FuzzyMath.fuzzyLessThan(channel, 256);
    }

    /** Writes a color as rgb() or rgba(). */
    private void writeRgb(SassColor color) {
        boolean opaque = FuzzyMath.fuzzyEquals(color.getAlpha(), 1.0);
        buffer.append(opaque ? "rgb(" : "rgba(");
        writeNumber(color.getRed());
        buffer.append(commaSeparator());
        writeNumber(color.getGreen());
        buffer.append(commaSeparator());
        writeNumber(color.getBlue());

        if (!opaque) {
            buffer.append(commaSeparator());
            writeNumber(color.getAlpha());
        }

        buffer.append(')');
    }

    /** Returns whether the given hex component is symmetrical (e.g. 0xFF). */
    private boolean isSymmetricalHex(int color) {
        return (color & 0xF) == (color >> 4);
    }

    /** Returns whether a color can use short hex (#abc for #aabbcc). */
    private boolean canUseShortHex(int red, int green, int blue) {
        return isSymmetricalHex(red) && isSymmetricalHex(green) && isSymmetricalHex(blue);
    }

    /** Writes a single hex component (two hex chars). */
    private void writeHexComponent(int color) {
        buffer.append(hexCharFor((color >> 4) & 0xF));
        buffer.append(hexCharFor(color & 0xF));
    }

    /** Returns the hex character for a value 0-15. */
    private static char hexCharFor(int value) {
        return (char) (value < 10 ? '0' + value : 'a' + value - 10);
    }

    // ---------------------------------------------------------------
    // String Serialization
    // ---------------------------------------------------------------

    /**
     * Writes a quoted string to the buffer.
     * Detects which quote character to use based on string contents.
     *
     * @param string          the string contents
     * @param forceDoubleQuote whether to always use double quotes
     */
    private void visitQuotedString(String string, boolean forceDoubleQuote) {
        boolean includesSingleQuote = false;
        boolean includesDoubleQuote = false;

        StringBuilder tempBuffer = forceDoubleQuote ? null : new StringBuilder();
        if (forceDoubleQuote) buffer.append('"');

        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch (ch) {
                case '\'':
                    if (forceDoubleQuote) {
                        buffer.append('\'');
                    } else if (includesDoubleQuote) {
                        visitQuotedString(string, true);
                        return;
                    } else {
                        includesSingleQuote = true;
                        tempBuffer.append('\'');
                    }
                    break;

                case '"':
                    if (forceDoubleQuote) {
                        buffer.append('\\');
                        buffer.append('"');
                    } else if (includesSingleQuote) {
                        visitQuotedString(string, true);
                        return;
                    } else {
                        includesDoubleQuote = true;
                        tempBuffer.append('"');
                    }
                    break;

                case '\\':
                    appendTo(forceDoubleQuote ? buffer : tempBuffer, '\\');
                    appendTo(forceDoubleQuote ? buffer : tempBuffer, '\\');
                    break;

                case '\n':
                case '\r':
                case '\f':
                    writeEscape(forceDoubleQuote ? buffer : tempBuffer, ch, string, i);
                    break;

                default:
                    if (ch < 0x20 || ch == 0x7F) {
                        // Control character
                        writeEscape(forceDoubleQuote ? buffer : tempBuffer, ch, string, i);
                    } else if (!isCompressed() && isPrivateUseBMP(ch)) {
                        writeEscape(forceDoubleQuote ? buffer : tempBuffer, ch, string, i);
                    } else if (!isCompressed() && Character.isHighSurrogate(ch)
                            && i + 1 < string.length()
                            && Character.isLowSurrogate(string.charAt(i + 1))) {
                        int codePoint = Character.toCodePoint(ch, string.charAt(i + 1));
                        if (isPrivateUseCodePoint(codePoint)) {
                            writeEscape(forceDoubleQuote ? buffer : tempBuffer, codePoint, string, i + 1);
                            i++;
                        } else {
                            appendTo(forceDoubleQuote ? buffer : tempBuffer, ch);
                            appendTo(forceDoubleQuote ? buffer : tempBuffer, string.charAt(i + 1));
                            i++;
                        }
                    } else {
                        appendTo(forceDoubleQuote ? buffer : tempBuffer, ch);
                    }
                    break;
            }
        }

        if (forceDoubleQuote) {
            buffer.append('"');
        } else {
            char quoteChar = includesDoubleQuote ? '\'' : '"';
            buffer.append(quoteChar);
            buffer.append(tempBuffer);
            buffer.append(quoteChar);
        }
    }

    /** Writes an unquoted string to the buffer. */
    private void visitUnquotedString(String string) {
        boolean afterNewline = false;
        for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch (ch) {
                case '\n':
                    buffer.append(' ');
                    afterNewline = true;
                    break;

                case ' ':
                    if (!afterNewline) buffer.append(' ');
                    break;

                default:
                    afterNewline = false;
                    if (!isCompressed() && isPrivateUseBMP(ch)) {
                        writeEscape(buffer, ch, string, i);
                    } else if (!isCompressed() && Character.isHighSurrogate(ch)
                            && i + 1 < string.length()
                            && Character.isLowSurrogate(string.charAt(i + 1))) {
                        int codePoint = Character.toCodePoint(ch, string.charAt(i + 1));
                        if (isPrivateUseCodePoint(codePoint)) {
                            writeEscape(buffer, codePoint, string, i + 1);
                            i++;
                        } else {
                            buffer.append(ch);
                            buffer.append(string.charAt(i + 1));
                            i++;
                        }
                    } else {
                        buffer.append(ch);
                    }
                    break;
            }
        }
    }

    /** Whether a BMP code unit is in the Private Use Area (U+E000..U+F8FF). */
    private static boolean isPrivateUseBMP(char ch) {
        return ch >= 0xE000 && ch <= 0xF8FF;
    }

    /** Whether a code point is in a Unicode Private Use Area. */
    private static boolean isPrivateUseCodePoint(int cp) {
        return (cp >= 0xE000 && cp <= 0xF8FF)
                || (cp >= 0xF0000 && cp <= 0xFFFFF)
                || (cp >= 0x100000 && cp <= 0x10FFFF);
    }

    /**
     * Writes a character as a hex escape sequence.
     *
     * @param target the buffer to write to
     * @param character the character code point to escape
     * @param string the source string
     * @param i the index of the last code unit of the character
     */
    private void writeEscape(StringBuilder target, int character, String string, int i) {
        target.append('\\');
        target.append(Integer.toHexString(character));

        if (i + 1 < string.length()) {
            char next = string.charAt(i + 1);
            if (isHexChar(next) || next == ' ' || next == '\t') {
                target.append(' ');
            }
        }
    }

    /** Whether the given character is a hex digit. */
    private static boolean isHexChar(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    /** Appends a character to the given StringBuilder. */
    private static void appendTo(StringBuilder sb, char ch) {
        sb.append(ch);
    }

    // ---------------------------------------------------------------
    // List / Map Helpers
    // ---------------------------------------------------------------

    /** Returns the separator string for list items with the given separator. */
    private String separatorString(ListSeparator separator) {
        return switch (separator) {
            case COMMA -> commaSeparator();
            case SLASH -> isCompressed() ? "/" : " / ";
            case SPACE -> " ";
            case UNDECIDED -> "";
        };
    }

    /** Returns whether an element needs parens in a list with the given separator. */
    private boolean elementNeedsParens(ListSeparator separator, Value value) {
        if (!(value instanceof SassList list)) return false;
        if (list.asList().size() <= 1 || list.hasBrackets()) return false;

        return switch (separator) {
            case COMMA -> list.getSeparator() == ListSeparator.COMMA;
            case SLASH -> list.getSeparator() == ListSeparator.COMMA
                    || list.getSeparator() == ListSeparator.SLASH;
            default -> list.getSeparator() != ListSeparator.UNDECIDED;
        };
    }

    /** Writes a value as a map key or value, adding parens as necessary. */
    private void writeMapElement(Value value) {
        boolean needsParens = value instanceof SassList list
                && list.getSeparator() == ListSeparator.COMMA
                && !list.hasBrackets();
        if (needsParens) buffer.append('(');
        value.accept(this);
        if (needsParens) buffer.append(')');
    }

    // ---------------------------------------------------------------
    // Selector Visit Methods
    // ---------------------------------------------------------------

    @Override
    public Void visitSelectorList(SelectorList list) {
        var complexes = inspect
                ? list.getComponents()
                : list.getComponents().stream()
                    .filter(c -> !c.isInvisible()).toList();

        boolean first = true;
        for (var complex : complexes) {
            if (first) {
                first = false;
            } else {
                buffer.append(',');
                if (complex.isLineBreak()) {
                    writeLineFeed();
                    writeIndentation();
                } else {
                    writeOptionalSpace();
                }
            }
            visitComplexSelector(complex);
        }
        return null;
    }

    @Override
    public Void visitComplexSelector(ComplexSelector complex) {
        writeCombinators(complex.getLeadingCombinators());
        if (!complex.getLeadingCombinators().isEmpty() && !complex.getComponents().isEmpty()) {
            writeOptionalSpace();
        }

        var components = complex.getComponents();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            visitCompoundSelector(component.getSelector());
            if (!component.getCombinators().isEmpty()) writeOptionalSpace();
            writeCombinators(component.getCombinators());
            if (i != components.size() - 1
                    && (!isCompressed() || component.getCombinators().isEmpty())) {
                buffer.append(' ');
            }
        }
        return null;
    }

    @Override
    public Void visitCompoundSelector(CompoundSelector compound) {
        int start = buffer.length();
        for (var simple : compound.getComponents()) {
            simple.accept(this);
        }
        // If we emit an empty compound, emit the universal selector.
        if (buffer.length() == start) buffer.append('*');
        return null;
    }

    @Override
    public Void visitClassSelector(ClassSelector node) {
        buffer.append('.');
        buffer.append(node.getName());
        return null;
    }

    @Override
    public Void visitIDSelector(IDSelector node) {
        buffer.append('#');
        buffer.append(node.getName());
        return null;
    }

    @Override
    public Void visitTypeSelector(TypeSelector node) {
        buffer.append(node.getName());
        return null;
    }

    @Override
    public Void visitUniversalSelector(UniversalSelector node) {
        if (node.getNamespace() != null) {
            buffer.append(node.getNamespace());
            buffer.append('|');
        }
        buffer.append('*');
        return null;
    }

    @Override
    public Void visitAttributeSelector(AttributeSelector attribute) {
        buffer.append('[');
        buffer.append(attribute.getName());

        if (attribute.getValue() != null) {
            buffer.append(attribute.getOp());

            // Check if the value is a valid CSS identifier (simplified check).
            String value = attribute.getValue();
            if (isIdentifier(value) && !value.startsWith("--")) {
                buffer.append(value);
                if (attribute.getModifier() != null) buffer.append(' ');
            } else {
                visitQuotedString(value, false);
                if (attribute.getModifier() != null) writeOptionalSpace();
            }

            if (attribute.getModifier() != null) {
                buffer.append(attribute.getModifier());
            }
        }
        buffer.append(']');
        return null;
    }

    @Override
    public Void visitPseudoSelector(PseudoSelector pseudo) {
        // :not(%a) is semantically identical to *, so emit nothing.
        if ("not".equals(pseudo.getName())
                && pseudo.getSelector() != null
                && pseudo.getSelector().isInvisible()) {
            return null;
        }

        buffer.append(':');
        // isSyntacticElement means it was written with :: (not a syntactic class)
        if (!pseudo.isSyntacticClass()) buffer.append(':');
        buffer.append(pseudo.getName());

        if (pseudo.getArgument() == null && pseudo.getSelector() == null) return null;

        buffer.append('(');
        if (pseudo.getArgument() != null) {
            buffer.append(pseudo.getArgument());
            if (pseudo.getSelector() != null) buffer.append(' ');
        }
        if (pseudo.getSelector() != null) {
            visitSelectorList(pseudo.getSelector());
        }
        buffer.append(')');
        return null;
    }

    @Override
    public Void visitPlaceholderSelector(PlaceholderSelector node) {
        buffer.append('%');
        buffer.append(node.getName());
        return null;
    }

    @Override
    public Void visitParentSelector(ParentSelector node) {
        buffer.append('&');
        if (node.getSuffix() != null) {
            buffer.append(node.getSuffix());
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Combinator Helper
    // ---------------------------------------------------------------

    /** Writes a list of combinators, with spaces in between in expanded mode. */
    private void writeCombinators(List<CssValue<Combinator>> combinators) {
        writeBetween(combinators, isCompressed() ? "" : " ",
                c -> buffer.append(c.getValue()));
    }

    // ---------------------------------------------------------------
    // Number Formatting
    // ---------------------------------------------------------------

    /**
     * Writes a number without exponent notation and with at most
     * {@link SassNumber#PRECISION} digits after the decimal point.
     */
    void writeNumber(double number) {
        // Handle fuzzy integers.
        Integer asInt = FuzzyMath.fuzzyAsInt(number);
        if (asInt != null && (!inspect || number == asInt)) {
            buffer.append(asInt);
            return;
        }

        String text = removeExponent(Double.toString(number));

        // Write the number at full precision in inspect mode.
        if (inspect) {
            buffer.append(text);
            return;
        }

        // Any double that's less than PRECISION + 2 digits long is safe
        // to emit directly.
        if (text.length() < SassNumber.PRECISION + 2) {
            if (isCompressed() && text.charAt(0) == '0') {
                text = text.substring(1);
            }
            buffer.append(text);
            return;
        }

        writeRounded(text);
    }

    /** Like writeNumber, but returns a string. */
    private String writeNumberToString(double number) {
        var saved = new StringBuilder(buffer);
        var originalLength = buffer.length();
        writeNumber(number);
        var result = buffer.substring(originalLength);
        buffer.setLength(originalLength);
        return result;
    }

    /**
     * If the text is in exponent notation, returns a string without it.
     * Otherwise returns the text as-is.
     */
    private String removeExponent(String text) {
        int eIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'e' || c == 'E') {
                eIndex = i;
                break;
            }
        }

        if (eIndex == -1) return text;

        boolean negative = text.charAt(0) == '-';
        var digits = new StringBuilder();
        digits.append(text.charAt(0));
        if (negative) {
            digits.append(text.charAt(1));
            if (eIndex > 3) {
                digits.append(text, 3, eIndex);
            }
        } else {
            if (eIndex > 2) {
                digits.append(text, 2, eIndex);
            }
        }

        int exponent = Integer.parseInt(text.substring(eIndex + 1));

        if (exponent > 0) {
            int additionalZeroes = exponent - (digits.length() - 1 - (negative ? 1 : 0));
            for (int i = 0; i < additionalZeroes; i++) {
                digits.append('0');
            }
            return digits.toString();
        } else {
            var result = new StringBuilder();
            if (negative) result.append('-');
            result.append("0.");
            for (int i = -1; i > exponent; i--) {
                result.append('0');
            }
            result.append(negative ? digits.substring(1) : digits);
            return result.toString();
        }
    }

    /**
     * Rounds a number text (without exponent notation) to PRECISION
     * digits after the decimal and writes it to the buffer.
     */
    private void writeRounded(String text) {
        // Dart serializes all doubles with trailing .0 for integers.
        if (text.endsWith(".0")) {
            buffer.append(text, 0, text.length() - 2);
            return;
        }

        // Find decimal point position
        int dotIndex = text.indexOf('.');
        if (dotIndex == -1) {
            buffer.append(text);
            return;
        }

        boolean negative = text.charAt(0) == '-';

        // Build array of digits with leading space for potential rounding carry
        int[] digits = new int[text.length() + 1];
        int digitsIndex = 1;

        int textIndex = negative ? 1 : 0;
        // Copy digits before decimal
        while (textIndex < text.length()) {
            char c = text.charAt(textIndex++);
            if (c == '.') break;
            digits[digitsIndex++] = c - '0';
        }
        int firstFractionalDigit = digitsIndex;

        // Only write at most PRECISION digits after the decimal
        int indexAfterPrecision = textIndex + SassNumber.PRECISION;
        if (indexAfterPrecision >= text.length()) {
            buffer.append(text);
            return;
        }

        // Copy fractional digits up to precision
        while (textIndex < indexAfterPrecision) {
            digits[digitsIndex++] = text.charAt(textIndex++) - '0';
        }

        // Round up if necessary
        if (text.charAt(textIndex) - '0' >= 5) {
            while (true) {
                int newDigit = ++digits[digitsIndex - 1];
                if (newDigit != 10) break;
                digitsIndex--;
            }
        }

        // Fix any digits that were set to 10 by carrying
        for (; digitsIndex < firstFractionalDigit; digitsIndex++) {
            digits[digitsIndex] = 0;
        }
        // Remove trailing fractional zeros
        while (digitsIndex > firstFractionalDigit && digits[digitsIndex - 1] == 0) {
            digitsIndex--;
        }

        // If rounded to exactly zero
        if (digitsIndex == 2 && digits[0] == 0 && digits[1] == 0) {
            buffer.append('0');
            return;
        }

        if (negative) buffer.append('-');

        // Write digits before decimal, skipping leading zero padding
        int writtenIndex = 0;
        if (digits[0] == 0) {
            writtenIndex++;
            if (isCompressed() && digits[1] == 0) writtenIndex++;
        }
        for (; writtenIndex < firstFractionalDigit; writtenIndex++) {
            buffer.append((char) ('0' + digits[writtenIndex]));
        }

        // Write fractional digits
        if (digitsIndex > firstFractionalDigit) {
            buffer.append('.');
            for (; writtenIndex < digitsIndex; writtenIndex++) {
                buffer.append((char) ('0' + digits[writtenIndex]));
            }
        }
    }

    // ---------------------------------------------------------------
    // Core Helper Methods
    // ---------------------------------------------------------------

    /** Writes spaces for the current indentation level. */
    private void writeIndentation() {
        if (isCompressed()) return;
        for (int i = 0; i < indentation * 2; i++) {
            buffer.append(' ');
        }
    }

    /** Writes a newline in expanded mode, nothing in compressed. */
    private void writeLineFeed() {
        if (!isCompressed()) buffer.append('\n');
    }

    /** Writes a space in expanded mode, nothing in compressed. */
    private void writeOptionalSpace() {
        if (!isCompressed()) buffer.append(' ');
    }

    /** Whether the style is compressed. */
    private boolean isCompressed() {
        return style == OutputStyle.COMPRESSED;
    }

    /** Returns the comma separator (with or without trailing space). */
    private String commaSeparator() {
        return isCompressed() ? "," : ", ";
    }

    /**
     * Emits a parent node's children in a block: {@code { ... }}.
     */
    /** Gets children from either CssParentNode or ModifiableCssParentNode. */
    private List<? extends CssNode> getChildren(CssNode node) {
        if (node instanceof CssParentNode p) return p.getChildren();
        if (node instanceof ModifiableCssParentNode p) return p.getChildren();
        return List.of();
    }

    private void visitChildren(CssNode parentNode) {
        var childList = getChildren(parentNode);
        buffer.append('{');

        CssNode prePrevious = null;
        CssNode previous = null;
        for (var child : childList) {
            if (isInvisible(child)) continue;

            if (previous != null && requiresSemicolon(previous)) {
                buffer.append(';');
            }

            if (isTrailingComment(child, previous != null ? previous : parentNode)) {
                writeOptionalSpace();
                withoutIndentation(() -> child.accept(this));
            } else {
                writeLineFeed();
                indent(() -> child.accept(this));
            }

            prePrevious = previous;
            previous = child;
        }

        if (previous != null) {
            if (requiresSemicolon(previous) && !isCompressed()) {
                buffer.append(';');
            }

            if (prePrevious == null && isTrailingComment(previous, parentNode)) {
                writeOptionalSpace();
            } else {
                writeLineFeed();
                writeIndentation();
            }
        }

        buffer.append('}');
    }

    /** Whether a node requires a semicolon after it. */
    private boolean requiresSemicolon(CssNode node) {
        if (node instanceof CssParentNode p) return p.isChildless();
        if (node instanceof ModifiableCssParentNode) {
            if (node instanceof ModifiableCssAtRule at) return at.isChildless();
            return false;
        }
        if (node instanceof CssComment || node instanceof ModifiableCssComment) return false;
        return true;
    }

    /**
     * Whether {@code node} represents a trailing comment after
     * {@code previous} in a sequence of nodes.
     */
    private boolean isTrailingComment(CssNode node, CssNode previous) {
        if (isCompressed()) return false;
        if (!(node instanceof CssComment) && !(node instanceof ModifiableCssComment)) return false;

        // Simplified: in the Java port we don't have full span-based
        // trailing comment detection. We approximate by checking if the
        // node and previous are on the same line.
        try {
            if (node.getSpan() == null || previous.getSpan() == null) return false;
            if (node.getSpan().sourceUrl() == null || previous.getSpan().sourceUrl() == null) return false;
            if (!node.getSpan().sourceUrl().equals(previous.getSpan().sourceUrl())) return false;

            // Check if previous span contains node span
            if (previous.getSpan().start().offset() <= node.getSpan().start().offset()
                    && previous.getSpan().end().offset() >= node.getSpan().end().offset()) {
                // Node is contained within previous
                return node.getSpan().start().line() == previous.getSpan().start().line();
            }

            return node.getSpan().start().line() == previous.getSpan().end().line();
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns whether a node is considered invisible. */
    private boolean isInvisible(CssNode node) {
        if (inspect) return false;
        // In compressed mode, also suppress non-preserved comments.
        if (isCompressed()) {
            if (node instanceof CssComment comment) {
                return !comment.isPreserved();
            }
            if (node instanceof ModifiableCssComment comment) {
                return !comment.isPreserved();
            }
        }
        return node.isInvisible();
    }

    /**
     * Writes each item in the iterable, with the separator between items.
     */
    private <T> void writeBetween(Iterable<T> iterable, String separator, Consumer<T> callback) {
        boolean first = true;
        for (T item : iterable) {
            if (first) {
                first = false;
            } else {
                buffer.append(separator);
            }
            callback.accept(item);
        }
    }

    /** Runs the callback with indentation incremented by one level. */
    private void indent(Runnable callback) {
        indentation++;
        callback.run();
        indentation--;
    }

    /** Runs the callback with indentation set to zero. */
    private void withoutIndentation(Runnable callback) {
        int saved = indentation;
        indentation = 0;
        callback.run();
        indentation = saved;
    }

    // ---------------------------------------------------------------
    // Text Indentation Utilities
    // ---------------------------------------------------------------

    /**
     * Returns the indentation level of the least-indented non-empty line
     * in {@code text} after the first.
     *
     * @return null if text contains no newlines; -1 if it contains newlines
     *         but no indented lines; otherwise the minimum indentation.
     */
    private Integer minimumIndentation(String text) {
        int i = 0;
        // Skip the first line
        while (i < text.length() && text.charAt(i) != '\n') {
            i++;
        }
        if (i >= text.length()) {
            return (i > 0 && text.charAt(i - 1) == '\n') ? -1 : null;
        }
        i++; // skip the newline

        Integer min = null;
        while (i < text.length()) {
            // Count leading whitespace
            int lineStart = i;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c != ' ' && c != '\t') break;
                i++;
            }
            if (i >= text.length() || text.charAt(i) == '\n') {
                // Empty or whitespace-only line
                if (i < text.length()) i++; // skip newline
                continue;
            }
            int col = i - lineStart;
            min = (min == null) ? col : Math.min(min, col);
            // Skip to end of line
            while (i < text.length() && text.charAt(i) != '\n') {
                i++;
            }
            if (i < text.length()) i++; // skip newline
        }

        return min != null ? min : -1;
    }

    /**
     * Writes {@code text} to the buffer, replacing {@code minimumIndentation}
     * with the current indentation for each non-empty line after the first.
     */
    private void writeWithIndent(String text, int minimumIndentation) {
        int i = 0;
        // Write the first line as-is
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                i++;
                break;
            }
            buffer.append(c);
            i++;
        }
        if (i >= text.length()) return;

        while (true) {
            // Scan forward past whitespace / empty lines
            int lineStart = i;
            int newlines = 1;
            boolean done = false;

            while (true) {
                if (i >= text.length()) {
                    buffer.append(' ');
                    return;
                }

                char c = text.charAt(i);
                if (c == ' ' || c == '\t') {
                    i++;
                } else if (c == '\n') {
                    lineStart = i + 1;
                    newlines++;
                    i++;
                } else {
                    break;
                }
            }

            for (int n = 0; n < newlines; n++) {
                buffer.append('\n');
            }
            writeIndentation();
            // Write from lineStart + minimumIndentation to end of line
            int start = lineStart + minimumIndentation;
            if (start < i) start = i; // Don't go past current position
            buffer.append(text, start, i);

            // Write until newline or end
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\n') {
                    i++;
                    break;
                }
                buffer.append(c);
                i++;
            }
            if (i >= text.length()) return;
        }
    }

    /** Trims ASCII whitespace from the right of the string. */
    private static String trimAsciiRight(String text) {
        int end = text.length();
        while (end > 0 && isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** Whether a character is ASCII whitespace. */
    private static boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f';
    }

    /**
     * Simplified CSS identifier check. Returns true if the string looks
     * like a valid CSS identifier (starts with a letter, underscore, or
     * hyphen followed by a non-digit, and contains only identifier characters).
     */
    private static boolean isIdentifier(String text) {
        if (text == null || text.isEmpty()) return false;
        int i = 0;
        char first = text.charAt(i);
        // Allow leading hyphens
        if (first == '-') {
            i++;
            if (i >= text.length()) return false;
            first = text.charAt(i);
            if (first == '-') {
                // Starts with "--"
                return true; // Custom properties are identifiers
            }
        }
        // First character (after optional hyphen) must be letter or underscore
        if (!isNameStart(text.charAt(i))) return false;
        i++;
        // Remaining characters must be name characters
        while (i < text.length()) {
            if (!isNameChar(text.charAt(i))) return false;
            i++;
        }
        return true;
    }

    /** Whether a character can start a CSS identifier name. */
    private static boolean isNameStart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || ch == '_' || ch > 0x7F;
    }

    /** Whether a character can appear in a CSS identifier name. */
    private static boolean isNameChar(char ch) {
        return isNameStart(ch) || (ch >= '0' && ch <= '9') || ch == '-';
    }
}
