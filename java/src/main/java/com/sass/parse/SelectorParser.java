package com.sass.parse;

import com.sass.ast.css.CssValue;
import com.sass.ast.selector.*;
import com.sass.util.FileSpan;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.sass.parse.CharCodes.*;
import static com.sass.util.Characters.*;

/**
 * A parser for CSS selectors.
 *
 * <p>Port of Dart sass selector.dart parser.</p>
 */
public class SelectorParser extends Parser {

    /**
     * Pseudo-class selectors that take unadorned selectors as arguments.
     */
    public static final Set<String> SELECTOR_PSEUDO_CLASSES = Set.of(
            "not", "is", "matches", "where", "current", "any", "has",
            "host", "host-context"
    );

    /**
     * Pseudo-element selectors that take unadorned selectors as arguments.
     */
    public static final Set<String> SELECTOR_PSEUDO_ELEMENTS = Set.of("slotted");

    /** Whether this parser allows the parent selector {@code &}. */
    private final boolean allowParent;

    /** Whether to parse the selector as plain CSS (no Sass extensions). */
    private final boolean plainCss;

    /**
     * Creates a parser that parses CSS selectors.
     *
     * @param contents the selector text to parse
     * @param url the source URL for error reporting (may be null)
     * @param allowParent whether the parent selector {@code &} is allowed
     * @param plainCss whether parsing plain CSS (no Sass extensions)
     */
    public SelectorParser(String contents, URI url, boolean allowParent, boolean plainCss) {
        super(contents, url);
        this.allowParent = allowParent;
        this.plainCss = plainCss;
    }

    /**
     * Creates a parser that parses CSS selectors with default settings.
     *
     * @param contents the selector text to parse
     */
    public SelectorParser(String contents) {
        this(contents, null, true, false);
    }

    /**
     * Creates a parser that parses CSS selectors with specified parent/plainCss.
     *
     * @param contents the selector text to parse
     * @param allowParent whether the parent selector {@code &} is allowed
     * @param plainCss whether parsing plain CSS
     */
    public SelectorParser(String contents, boolean allowParent, boolean plainCss) {
        this(contents, null, allowParent, plainCss);
    }

    /**
     * Parses a complete selector list.
     *
     * @return the parsed selector list
     */
    public SelectorList parse() {
        return wrapSpanFormatException(() -> {
            var selector = selectorList();
            if (!scanner.isDone()) {
                throw scanner.error("expected selector.");
            }
            return selector;
        });
    }

    /**
     * Parses a single complex selector.
     *
     * @return the parsed complex selector
     */
    public ComplexSelector parseComplexSelector() {
        return wrapSpanFormatException(() -> {
            var complex = complexSelector(false);
            if (!scanner.isDone()) {
                throw scanner.error("expected selector.");
            }
            return complex;
        });
    }

    /**
     * Parses a compound selector.
     *
     * @return the parsed compound selector
     */
    public CompoundSelector parseCompoundSelector() {
        return wrapSpanFormatException(() -> {
            var compound = compoundSelector();
            if (!scanner.isDone()) {
                throw scanner.error("expected selector.");
            }
            return compound;
        });
    }

    /**
     * Parses a single simple selector.
     *
     * @return the parsed simple selector
     */
    public SimpleSelector parseSimpleSelector() {
        return wrapSpanFormatException(() -> {
            var simple = simpleSelector(null);
            if (!scanner.isDone()) {
                throw scanner.error("unexpected token.");
            }
            return simple;
        });
    }

    // =========================================================================
    // Internal parsing methods
    // =========================================================================

    /**
     * Consumes a selector list (comma-separated complex selectors).
     */
    private SelectorList selectorList() {
        var start = scanner.getState();
        int previousLine = scanner.getLine();
        var components = new ArrayList<ComplexSelector>();
        components.add(complexSelector(false));

        consumeWhitespace();
        while (scanner.scanChar($comma)) {
            consumeWhitespace();
            if (scanner.peekChar() == $comma) continue;
            if (scanner.isDone()) break;

            boolean lineBreak = scanner.getLine() != previousLine;
            if (lineBreak) previousLine = scanner.getLine();
            components.add(complexSelector(lineBreak));
        }

        return new SelectorList(components, spanFrom(start));
    }

    /**
     * Consumes a complex selector.
     *
     * @param lineBreak indicates that there was a line break before this selector
     */
    private ComplexSelector complexSelector(boolean lineBreak) {
        var start = scanner.getState();
        var componentStart = scanner.getState();

        CompoundSelector lastCompound = null;
        var combinators = new ArrayList<CssValue<Combinator>>();
        List<CssValue<Combinator>> initialCombinators = null;
        var components = new ArrayList<ComplexSelectorComponent>();

        loop:
        while (true) {
            consumeWhitespace();

            int next = scanner.peekChar();
            switch (next) {
                case $plus: {
                    var combinatorStart = scanner.getState();
                    scanner.readChar();
                    combinators.add(new CssValue<>(Combinator.NEXT_SIBLING, spanFrom(combinatorStart)));
                    break;
                }

                case $gt: {
                    var combinatorStart = scanner.getState();
                    scanner.readChar();
                    combinators.add(new CssValue<>(Combinator.CHILD, spanFrom(combinatorStart)));
                    break;
                }

                case $tilde: {
                    var combinatorStart = scanner.getState();
                    scanner.readChar();
                    combinators.add(new CssValue<>(Combinator.FOLLOWING_SIBLING, spanFrom(combinatorStart)));
                    break;
                }

                case -1:
                    break loop;

                case $lbracket:
                case $dot:
                case $hash:
                case $percent:
                case $colon:
                case $ampersand:
                case $asterisk:
                case $pipe:
                    // Fall through to compound selector parsing below
                    if (lastCompound != null) {
                        components.add(new ComplexSelectorComponent(
                                lastCompound, List.copyOf(combinators), spanFrom(componentStart)));
                    } else if (!combinators.isEmpty()) {
                        assert initialCombinators == null;
                        initialCombinators = List.copyOf(combinators);
                        componentStart = scanner.getState();
                    }

                    lastCompound = compoundSelector();
                    combinators = new ArrayList<>();
                    if (scanner.peekChar() == $ampersand) {
                        throw scanner.error(
                                "\"&\" may only used at the beginning of a compound selector.");
                    }
                    break;

                default:
                    if (lookingAtIdentifier()) {
                        if (lastCompound != null) {
                            components.add(new ComplexSelectorComponent(
                                    lastCompound, List.copyOf(combinators), spanFrom(componentStart)));
                        } else if (!combinators.isEmpty()) {
                            assert initialCombinators == null;
                            initialCombinators = List.copyOf(combinators);
                            componentStart = scanner.getState();
                        }

                        lastCompound = compoundSelector();
                        combinators = new ArrayList<>();
                        if (scanner.peekChar() == $ampersand) {
                            throw scanner.error(
                                    "\"&\" may only used at the beginning of a compound selector.");
                        }
                    } else {
                        break loop;
                    }
                    break;
            }
        }

        if (!combinators.isEmpty() && plainCss) {
            throw scanner.error("expected selector.");
        } else if (lastCompound != null) {
            components.add(new ComplexSelectorComponent(
                    lastCompound, List.copyOf(combinators), spanFrom(componentStart)));
        } else if (!combinators.isEmpty()) {
            initialCombinators = List.copyOf(combinators);
        } else {
            throw scanner.error("expected selector.");
        }

        return new ComplexSelector(
                initialCombinators != null ? initialCombinators : List.of(),
                components,
                lineBreak,
                spanFrom(start));
    }

    /**
     * Consumes a compound selector.
     */
    private CompoundSelector compoundSelector() {
        var start = scanner.getState();
        var components = new ArrayList<SimpleSelector>();
        components.add(simpleSelector(null));

        while (isSimpleSelectorStart(scanner.peekChar())) {
            // When plainCss is true, allow parent selector in subsequent positions
            components.add(simpleSelector(plainCss ? Boolean.TRUE : null));
        }

        return new CompoundSelector(components, spanFrom(start));
    }

    /**
     * Consumes a simple selector.
     *
     * @param allowParentOverride if non-null, overrides the default allowParent setting
     */
    private SimpleSelector simpleSelector(Boolean allowParentOverride) {
        var start = scanner.getState();
        boolean effectiveAllowParent = allowParentOverride != null
                ? allowParentOverride
                : this.allowParent;

        int next = scanner.peekChar();
        switch (next) {
            case $lbracket:
                return attributeSelector();
            case $dot:
                return classSelector();
            case $hash:
                return idSelector();
            case $percent: {
                var selector = placeholderSelector();
                if (plainCss) {
                    throw error("Placeholder selectors aren't allowed in plain CSS.",
                            spanFrom(start));
                }
                return selector;
            }
            case $colon:
                return pseudoSelector();
            case $ampersand: {
                var selector = parentSelector();
                if (!effectiveAllowParent) {
                    throw error("Parent selectors aren't allowed here.",
                            spanFrom(start));
                }
                return selector;
            }
            default:
                return typeOrUniversalSelector();
        }
    }

    /**
     * Consumes an attribute selector.
     */
    private AttributeSelector attributeSelector() {
        var start = scanner.getState();
        scanner.expectChar($lbracket);
        consumeWhitespace();

        var name = attributeName();
        consumeWhitespace();

        if (scanner.scanChar($rbracket)) {
            return new AttributeSelector(name, spanFrom(start));
        }

        var op = attributeOperator();
        consumeWhitespace();

        int next = scanner.peekChar();
        String value;
        if (next == $singleQuote || next == $doubleQuote) {
            value = string();
        } else {
            value = identifier();
        }
        consumeWhitespace();

        next = scanner.peekChar();
        String modifier = null;
        if (next != -1 && isAlphabetic(next)) {
            modifier = String.valueOf((char) scanner.readChar());
        }

        scanner.expectChar($rbracket);
        return new AttributeSelector(name, op, value, modifier, spanFrom(start));
    }

    /**
     * Consumes a qualified name as part of an attribute selector.
     */
    private QualifiedName attributeName() {
        if (scanner.scanChar($asterisk)) {
            scanner.expectChar($pipe);
            return new QualifiedName(identifier(), "*");
        }

        if (scanner.scanChar($pipe)) {
            return new QualifiedName(identifier(), "");
        }

        var nameOrNamespace = identifier();
        if (scanner.peekChar() != $pipe || scanner.peekChar(1) == $equal) {
            return new QualifiedName(nameOrNamespace);
        }

        scanner.readChar(); // consume '|'
        return new QualifiedName(identifier(), nameOrNamespace);
    }

    /**
     * Consumes an attribute selector's operator.
     */
    private AttributeOperator attributeOperator() {
        int start = scanner.getPosition();
        int ch = scanner.readChar();
        switch (ch) {
            case $equal:
                return AttributeOperator.EQUAL;
            case $tilde:
                scanner.expectChar($equal);
                return AttributeOperator.INCLUDE;
            case $pipe:
                scanner.expectChar($equal);
                return AttributeOperator.DASH;
            case $caret:
                scanner.expectChar($equal);
                return AttributeOperator.PREFIX;
            case $dollar:
                scanner.expectChar($equal);
                return AttributeOperator.SUFFIX;
            case $asterisk:
                scanner.expectChar($equal);
                return AttributeOperator.SUBSTRING;
            default:
                throw scanner.error("Expected \"]\".", start, 1);
        }
    }

    /**
     * Consumes a class selector.
     */
    private ClassSelector classSelector() {
        var start = scanner.getState();
        scanner.expectChar($dot);
        var name = identifier();
        return new ClassSelector(name, spanFrom(start));
    }

    /**
     * Consumes an ID selector.
     */
    private IDSelector idSelector() {
        var start = scanner.getState();
        scanner.expectChar($hash);
        var name = identifier();
        return new IDSelector(name, spanFrom(start));
    }

    /**
     * Consumes a placeholder selector.
     */
    private PlaceholderSelector placeholderSelector() {
        var start = scanner.getState();
        scanner.expectChar($percent);
        var name = identifier();
        return new PlaceholderSelector(name, spanFrom(start));
    }

    /**
     * Consumes a parent selector.
     */
    private ParentSelector parentSelector() {
        var start = scanner.getState();
        scanner.expectChar($ampersand);
        String suffix = lookingAtIdentifierBody() ? identifierBody() : null;
        if (plainCss && suffix != null) {
            throw scanner.error(
                    "Parent selectors can't have suffixes in plain CSS.",
                    start.position(),
                    scanner.getPosition() - start.position());
        }
        return new ParentSelector(suffix, spanFrom(start));
    }

    /**
     * Consumes a pseudo selector (pseudo-class or pseudo-element).
     */
    private PseudoSelector pseudoSelector() {
        var start = scanner.getState();
        scanner.expectChar($colon);
        boolean element = scanner.scanChar($colon);
        var name = identifier();

        if (!scanner.scanChar($lparen)) {
            // isClass = !element, isSyntacticClass = !element
            return new PseudoSelector(name, !element, !element, null, null, spanFrom(start));
        }
        consumeWhitespace();

        var unvendored = unvendor(name);
        String argument = null;
        SelectorList selector = null;

        if (element) {
            if (SELECTOR_PSEUDO_ELEMENTS.contains(unvendored)) {
                selector = selectorList();
            } else {
                argument = declarationValue(true);
            }
        } else if (SELECTOR_PSEUDO_CLASSES.contains(unvendored)) {
            selector = selectorList();
        } else if ("nth-child".equals(unvendored) || "nth-last-child".equals(unvendored)) {
            argument = aNPlusB();
            consumeWhitespace();

            // Check if the character before whitespace was whitespace, and next is not ')'
            if (isWhitespace(scanner.peekChar(-1)) && scanner.peekChar() != $rparen) {
                expectIdentifier("of");
                argument += " of";
                consumeWhitespace();
                selector = selectorList();
            }
        } else {
            String raw = declarationValue(true);
            argument = raw.stripTrailing();
        }

        scanner.expectChar($rparen);

        return new PseudoSelector(name, !element, !element, argument, selector, spanFrom(start));
    }

    /**
     * Consumes an {@code An+B} production and returns its text.
     *
     * @see <a href="https://drafts.csswg.org/css-syntax-3/#anb-microsyntax">An+B microsyntax</a>
     */
    private String aNPlusB() {
        var buffer = new StringBuilder();
        int next = scanner.peekChar();

        if (next == $e || next == $E) {
            expectIdentifier("even");
            return "even";
        }

        if (next == $o || next == $O) {
            expectIdentifier("odd");
            return "odd";
        }

        if (next == $plus || next == $minus) {
            buffer.appendCodePoint(scanner.readChar());
        }

        if (isDigit(scanner.peekChar())) {
            do {
                buffer.appendCodePoint(scanner.readChar());
            } while (isDigit(scanner.peekChar()));
            consumeWhitespace();
            if (!scanIdentChar($n)) return buffer.toString();
        } else {
            expectIdentChar($n);
        }
        buffer.append('n');
        consumeWhitespace();

        next = scanner.peekChar();
        if (next != $plus && next != $minus) return buffer.toString();
        buffer.appendCodePoint(scanner.readChar());
        consumeWhitespace();

        if (!isDigit(scanner.peekChar())) {
            throw scanner.error("Expected a number.");
        }
        do {
            buffer.appendCodePoint(scanner.readChar());
        } while (isDigit(scanner.peekChar()));
        return buffer.toString();
    }

    /**
     * Consumes a type selector or a universal selector.
     * These are combined because either one could start with {@code *}.
     */
    private SimpleSelector typeOrUniversalSelector() {
        var start = scanner.getState();

        if (scanner.scanChar($asterisk)) {
            if (!scanner.scanChar($pipe)) {
                return new UniversalSelector(null, spanFrom(start));
            }
            if (scanner.scanChar($asterisk)) {
                return new UniversalSelector("*", spanFrom(start));
            }
            return new TypeSelector(
                    new QualifiedName(identifier(), "*"), spanFrom(start));
        } else if (scanner.scanChar($pipe)) {
            if (scanner.scanChar($asterisk)) {
                return new UniversalSelector("", spanFrom(start));
            }
            return new TypeSelector(
                    new QualifiedName(identifier(), ""), spanFrom(start));
        }

        var nameOrNamespace = identifier();
        if (!scanner.scanChar($pipe)) {
            return new TypeSelector(
                    new QualifiedName(nameOrNamespace), spanFrom(start));
        } else if (scanner.scanChar($asterisk)) {
            return new UniversalSelector(nameOrNamespace, spanFrom(start));
        } else {
            return new TypeSelector(
                    new QualifiedName(identifier(), nameOrNamespace), spanFrom(start));
        }
    }

    /**
     * Returns whether the given character can start a simple selector in the
     * middle of a compound selector.
     */
    private boolean isSimpleSelectorStart(int character) {
        return switch (character) {
            case $asterisk, $lbracket, $dot, $hash, $percent, $colon -> true;
            case $ampersand -> plainCss;
            default -> false;
        };
    }

    /**
     * Consumes whitespace. The value of consumeNewlines is not relevant for
     * this class, so we always pass true.
     */
    private void consumeWhitespace() {
        whitespace(true);
    }

    // =========================================================================
    // String Utilities
    // =========================================================================

    /**
     * Returns the un-vendored version of a CSS identifier.
     * For example, "-webkit-foo" becomes "foo".
     */
    private static String unvendor(String name) {
        if (name.length() < 2) return name;
        if (name.charAt(0) != '-') return name;
        if (name.charAt(1) == '-') return name;

        for (int i = 2; i < name.length(); i++) {
            if (name.charAt(i) == '-') return name.substring(i + 1);
        }
        return name;
    }

    /**
     * Returns whether the two strings are equal, case-insensitively.
     */
    static boolean equalsIgnoreCase(String s1, String s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;
        if (s1.length() != s2.length()) return false;
        for (int i = 0; i < s1.length(); i++) {
            if (!characterEqualsIgnoreCase(s1.charAt(i), s2.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
