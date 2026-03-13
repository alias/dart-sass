package com.sass.parse;

import com.sass.exception.SassFormatException;
import com.sass.util.Characters;
import com.sass.util.FileSpan;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import static com.sass.parse.CharCodes.*;
import static com.sass.util.Characters.*;

/**
 * The abstract base class for all parsers.
 *
 * This provides utility methods and common token parsing. Unless specified
 * otherwise, a parse method throws a {@link SassFormatException} if it fails
 * to parse.
 *
 * Port of Dart sass parser.dart.
 */
public abstract class Parser {

    /** The scanner that scans through the text being parsed. */
    protected final SpanScanner scanner;

    /**
     * Functional interface for character predicates used by {@link #scanCharIf}.
     */
    @FunctionalInterface
    public interface CharPredicate {
        boolean test(int ch);
    }

    // =========================================================================
    // Static methods
    // =========================================================================

    /**
     * Parses {@code text} as a CSS identifier and returns the result.
     *
     * @throws SassFormatException if parsing fails
     */
    public static String parseIdentifier(String text) {
        return new InlineParser(text).doParseIdentifier();
    }

    /**
     * Returns whether {@code text} is a valid CSS identifier.
     */
    public static boolean isIdentifier(String text) {
        try {
            parseIdentifier(text);
            return true;
        } catch (SassFormatException e) {
            return false;
        }
    }

    /**
     * Returns whether {@code text} starts like a variable declaration.
     * Ignores everything after the {@code :}.
     */
    public static boolean isVariableDeclarationLike(String text) {
        return new InlineParser(text).doIsVariableDeclarationLike();
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates a parser that will scan {@code contents}.
     *
     * @param contents the source text to parse
     * @param url      the URL of the source, used for error reporting (may be null)
     */
    protected Parser(String contents, URI url) {
        this.scanner = new SpanScanner(contents, url);
    }

    /**
     * Creates a parser that will scan {@code contents} with no URL.
     */
    protected Parser(String contents) {
        this(contents, (URI) null);
    }

    // =========================================================================
    // Tokens: Whitespace
    // =========================================================================

    /**
     * Consumes whitespace, including any comments.
     *
     * @param consumeNewlines if true, the indented syntax will consume newlines
     *                        as whitespace. Subclasses may override
     *                        {@link #whitespaceWithoutComments} to change behavior.
     */
    protected void whitespace(boolean consumeNewlines) {
        do {
            whitespaceWithoutComments(consumeNewlines);
        } while (scanComment());
    }

    /**
     * Consumes whitespace, but not comments.
     *
     * <p>The base implementation consumes all whitespace characters regardless of
     * {@code consumeNewlines}. Subclasses (e.g. indented syntax) may override to
     * respect the flag.</p>
     */
    protected void whitespaceWithoutComments(boolean consumeNewlines) {
        while (!scanner.isDone() && isWhitespace(scanner.peekChar())) {
            scanner.readChar();
        }
    }

    /**
     * Consumes spaces and tabs only (no newlines).
     */
    protected void spaces() {
        while (!scanner.isDone() && isSpaceOrTab(scanner.peekChar())) {
            scanner.readChar();
        }
    }

    /**
     * Consumes and ignores a comment if possible.
     *
     * @return whether a comment was consumed
     */
    protected boolean scanComment() {
        if (scanner.peekChar() != $slash) return false;

        int next = scanner.peekChar(1);
        if (next == $slash) {
            return silentComment();
        } else if (next == $asterisk) {
            loudComment();
            return true;
        }
        return false;
    }

    /**
     * Like {@link #whitespace}, but throws an error if no whitespace is consumed.
     */
    protected void expectWhitespace(boolean consumeNewlines) {
        if (scanner.isDone() || !(isWhitespace(scanner.peekChar()) || scanComment())) {
            throw scanner.error("Expected whitespace.");
        }
        whitespace(consumeNewlines);
    }

    /**
     * Consumes and ignores a single silent (Sass-style {@code //}) comment,
     * not including the trailing newline.
     *
     * @return whether the comment was consumed
     */
    protected boolean silentComment() {
        scanner.expect("//");
        while (!scanner.isDone() && !isNewline(scanner.peekChar())) {
            scanner.readChar();
        }
        return true;
    }

    /**
     * Consumes and ignores a loud (CSS-style) comment ({@code /* ... * /}).
     */
    protected void loudComment() {
        scanner.expect("/*");
        while (true) {
            int next = scanner.readChar();
            if (next != $asterisk) continue;

            do {
                next = scanner.readChar();
            } while (next == $asterisk);
            if (next == $slash) break;
        }
    }

    // =========================================================================
    // Tokens: Identifiers
    // =========================================================================

    /**
     * Consumes a plain CSS identifier.
     *
     * <p>If {@code normalize} is true, underscores are converted to hyphens
     * (Sass convention).</p>
     *
     * <p>If {@code unit} is true, a {@code -} followed by a digit is not parsed
     * as part of the identifier. This ensures that {@code 1px-2px} parses as
     * subtraction.</p>
     */
    protected String identifier(boolean normalize, boolean unit) {
        var text = new StringBuilder();
        if (scanner.scanChar($minus)) {
            text.append((char) $minus);

            if (scanner.scanChar($minus)) {
                text.append((char) $minus);
                identifierBody(text, normalize, unit);
                return text.toString();
            }
        }

        int next = scanner.peekChar();
        if (next == -1) {
            throw scanner.error("Expected identifier.");
        } else if (next == $underscore && normalize) {
            scanner.readChar();
            text.append((char) $minus);
        } else if (isNameStart(next)) {
            text.appendCodePoint(scanner.readChar());
        } else if (next == $backslash) {
            text.append(escape(true));
        } else {
            throw scanner.error("Expected identifier.");
        }

        identifierBody(text, normalize, unit);
        return text.toString();
    }

    /**
     * Consumes a plain CSS identifier with default settings (normalize=false, unit=false).
     */
    protected String identifier() {
        return identifier(false, false);
    }

    /**
     * Consumes a chunk of a plain CSS identifier after the name start.
     *
     * @return the identifier body text
     */
    protected String identifierBody() {
        var text = new StringBuilder();
        identifierBody(text, false, false);
        if (text.isEmpty()) {
            throw scanner.error("Expected identifier body.");
        }
        return text.toString();
    }

    /**
     * Parses identifier body characters into {@code text}.
     */
    private void identifierBody(StringBuilder text, boolean normalize, boolean unit) {
        while (true) {
            int next = scanner.peekChar();
            if (next == -1) {
                break;
            } else if (next == $minus && unit) {
                // Disallow `-` followed by a dot or a digit in units.
                int after = scanner.peekChar(1);
                if (after == $dot || isDigit(after)) break;
                text.appendCodePoint(scanner.readChar());
            } else if (next == $underscore && normalize) {
                scanner.readChar();
                text.append((char) $minus);
            } else if (isName(next)) {
                text.appendCodePoint(scanner.readChar());
            } else if (next == $backslash) {
                text.append(escape(false));
            } else {
                break;
            }
        }
    }

    /**
     * Returns whether the scanner is immediately before a plain CSS identifier.
     */
    protected boolean lookingAtIdentifier() {
        return lookingAtIdentifier(0);
    }

    /**
     * Returns whether the scanner is immediately before a plain CSS identifier,
     * looking {@code forward} characters ahead.
     *
     * <p>This is based on the CSS algorithm, but assumes all backslashes start escapes.</p>
     */
    protected boolean lookingAtIdentifier(int forward) {
        int first = scanner.peekChar(forward);
        if (isNameStart(first) || first == $backslash) return true;
        if (first != $minus) return false;

        int second = scanner.peekChar(forward + 1);
        return isNameStart(second) || second == $backslash || second == $minus;
    }

    /**
     * Returns whether the scanner is immediately before a sequence of characters
     * that could be part of a plain CSS identifier body.
     */
    protected boolean lookingAtIdentifierBody() {
        int next = scanner.peekChar();
        return next != -1 && (isName(next) || next == $backslash);
    }

    /**
     * Consumes an identifier if its name exactly matches {@code text}
     * (case-insensitive by default).
     *
     * @return whether the identifier was consumed
     */
    protected boolean scanIdentifier(String text) {
        return scanIdentifier(text, false);
    }

    /**
     * Consumes an identifier if its name exactly matches {@code text}.
     *
     * @param caseSensitive if true, matching is case-sensitive
     * @return whether the identifier was consumed
     */
    protected boolean scanIdentifier(String text, boolean caseSensitive) {
        if (!lookingAtIdentifier()) return false;

        var start = scanner.getState();
        if (consumeIdentifier(text, caseSensitive) && !lookingAtIdentifierBody()) {
            return true;
        } else {
            scanner.setState(start);
            return false;
        }
    }

    /**
     * Returns whether an identifier whose name exactly matches {@code text} is at
     * the current scanner position (case-insensitive by default).
     *
     * <p>This doesn't move the scan pointer forward.</p>
     */
    protected boolean matchesIdentifier(String text) {
        return matchesIdentifier(text, false);
    }

    /**
     * Returns whether an identifier whose name exactly matches {@code text} is at
     * the current scanner position.
     *
     * @param caseSensitive if true, matching is case-sensitive
     */
    protected boolean matchesIdentifier(String text, boolean caseSensitive) {
        if (!lookingAtIdentifier()) return false;

        var start = scanner.getState();
        boolean result = consumeIdentifier(text, caseSensitive) && !lookingAtIdentifierBody();
        scanner.setState(start);
        return result;
    }

    /**
     * Consumes {@code text} as an identifier but doesn't verify whether there's
     * additional identifier text afterwards.
     *
     * @return true if the full text is consumed, false otherwise (does not reset scan pointer)
     */
    private boolean consumeIdentifier(String text, boolean caseSensitive) {
        for (int i = 0; i < text.length(); i++) {
            if (!scanIdentChar(text.charAt(i), caseSensitive)) return false;
        }
        return true;
    }

    /**
     * Consumes an identifier and asserts that its name exactly matches {@code text}
     * (case-insensitive by default).
     *
     * @throws SassFormatException if the identifier doesn't match
     */
    protected void expectIdentifier(String text) {
        expectIdentifier(text, null, false);
    }

    /**
     * Consumes an identifier and asserts that its name exactly matches {@code text}.
     *
     * @param name          the human-readable name used in error messages (defaults to quoted text)
     * @param caseSensitive if true, matching is case-sensitive
     * @throws SassFormatException if the identifier doesn't match
     */
    protected void expectIdentifier(String text, String name, boolean caseSensitive) {
        if (name == null) name = "\"" + text + "\"";

        int start = scanner.getPosition();
        for (int i = 0; i < text.length(); i++) {
            if (scanIdentChar(text.charAt(i), caseSensitive)) continue;
            throw scanner.error("Expected " + name + ".", start, 0);
        }

        if (!lookingAtIdentifierBody()) return;
        throw scanner.error("Expected " + name, start, 0);
    }

    /**
     * Consumes the next character or escape sequence if it matches {@code expected}
     * (case-insensitive by default).
     *
     * @return whether the character was consumed
     */
    protected boolean scanIdentChar(int expected) {
        return scanIdentChar(expected, false);
    }

    /**
     * Consumes the next character or escape sequence if it matches {@code expected}.
     *
     * @param caseSensitive if true, matching is case-sensitive
     * @return whether the character was consumed
     */
    protected boolean scanIdentChar(int expected, boolean caseSensitive) {
        int next = scanner.peekChar();
        if (next != -1 && matches(expected, next, caseSensitive)) {
            scanner.readChar();
            return true;
        }

        if (next == $backslash) {
            var start = scanner.getState();
            if (matches(expected, escapeCharacter(), caseSensitive)) return true;
            scanner.setState(start);
        }
        return false;
    }

    /**
     * Consumes the next character or escape sequence and asserts it matches
     * {@code expected} (case-insensitive by default).
     *
     * @throws SassFormatException if the character doesn't match
     */
    protected void expectIdentChar(int expected) {
        expectIdentChar(expected, false);
    }

    /**
     * Consumes the next character or escape sequence and asserts it matches
     * {@code expected}.
     *
     * @param caseSensitive if true, matching is case-sensitive
     * @throws SassFormatException if the character doesn't match
     */
    protected void expectIdentChar(int expected, boolean caseSensitive) {
        if (scanIdentChar(expected, caseSensitive)) return;

        throw scanner.error(
                "Expected \"" + new String(Character.toChars(expected)) + "\".",
                scanner.getPosition(), 0);
    }

    /**
     * Consumes a Sass variable name and returns its name without the dollar sign.
     * Identifiers are normalized (underscores become hyphens).
     */
    protected String variableName() {
        scanner.expectChar($dollar);
        return identifier(true, false);
    }

    // =========================================================================
    // Tokens: Strings / Numbers
    // =========================================================================

    /**
     * Consumes a plain CSS string (single or double quoted).
     *
     * <p>Returns the parsed contents of the string -- that is, it doesn't include
     * quotes and its escapes are resolved.</p>
     */
    protected String string() {
        int quote = scanner.readChar();
        if (quote != $singleQuote && quote != $doubleQuote) {
            throw scanner.error("Expected string.", scanner.getPosition() - 1, 1);
        }

        var buffer = new StringBuilder();
        while (true) {
            int next = scanner.peekChar();
            if (next == quote) {
                scanner.readChar();
                break;
            } else if (next == -1 || isNewline(next)) {
                throw scanner.error("Expected " + (char) quote + ".");
            } else if (next == $backslash) {
                int afterBackslash = scanner.peekChar(1);
                if (afterBackslash != -1 && isNewline(afterBackslash)) {
                    scanner.readChar();
                    scanner.readChar();
                } else {
                    buffer.appendCodePoint(escapeCharacter());
                }
            } else {
                buffer.appendCodePoint(scanner.readChar());
            }
        }

        return buffer.toString();
    }

    /**
     * Consumes and returns a natural number (a non-negative integer) as a double.
     * Does not support scientific notation.
     */
    protected double naturalNumber() {
        int first = scanner.readChar();
        if (!isDigit(first)) {
            throw scanner.error("Expected digit.", scanner.getPosition() - 1, 1);
        }

        double number = asDecimal(first);
        while (scanner.peekChar() != -1 && isDigit(scanner.peekChar())) {
            number *= 10;
            number += asDecimal(scanner.readChar());
        }
        return number;
    }

    // =========================================================================
    // Tokens: Declaration Value
    // =========================================================================

    /**
     * Consumes tokens until it reaches a top-level {@code ";"}, {@code ")"},
     * {@code "]"}, or {@code "}"} and returns their contents as a string.
     *
     * @param allowEmpty if false (the default), this requires at least one token
     */
    protected String declarationValue(boolean allowEmpty) {
        var buffer = new StringBuilder();
        Deque<Integer> brackets = new ArrayDeque<>();
        boolean wroteNewline = false;

        loop:
        while (true) {
            int next = scanner.peekChar();
            switch (next) {
                case -1:  // end of input
                    break loop;

                case $backslash:
                    buffer.append(escape(true));
                    wroteNewline = false;
                    break;

                case $doubleQuote:
                case $singleQuote:
                    buffer.append(rawText(this::string));
                    wroteNewline = false;
                    break;

                case $slash:
                    if (scanner.peekChar(1) == $asterisk) {
                        buffer.append(rawText(this::loudComment));
                    } else {
                        buffer.appendCodePoint(scanner.readChar());
                    }
                    wroteNewline = false;
                    break;

                case $space:
                case $tab:
                    int afterSpace = scanner.peekChar(1);
                    if (wroteNewline || afterSpace == -1 || !isWhitespace(afterSpace)) {
                        buffer.append((char) $space);
                    }
                    scanner.readChar();
                    break;

                case $lf:
                case $cr:
                case $ff: {
                    int prev = scanner.peekChar(-1);
                    if (prev == -1 || !isNewline(prev)) buffer.append('\n');
                    scanner.readChar();
                    wroteNewline = true;
                    break;
                }

                case $lparen:
                case $lbrace:
                case $lbracket:
                    buffer.appendCodePoint(next);
                    brackets.push(opposite(scanner.readChar()));
                    wroteNewline = false;
                    break;

                case $rparen:
                case $rbrace:
                case $rbracket:
                    if (brackets.isEmpty()) break loop;
                    buffer.appendCodePoint(next);
                    scanner.expectChar(brackets.pop());
                    wroteNewline = false;
                    break;

                case $semicolon:
                    if (brackets.isEmpty()) break loop;
                    buffer.appendCodePoint(scanner.readChar());
                    break;

                case $u:
                case $U: {
                    String url = tryUrl();
                    if (url != null) {
                        buffer.append(url);
                    } else {
                        buffer.appendCodePoint(scanner.readChar());
                    }
                    wroteNewline = false;
                    break;
                }

                default:
                    if (lookingAtIdentifier()) {
                        buffer.append(identifier());
                    } else {
                        buffer.appendCodePoint(scanner.readChar());
                    }
                    wroteNewline = false;
                    break;
            }
        }

        if (!brackets.isEmpty()) scanner.expectChar(brackets.peek());
        if (!allowEmpty && buffer.isEmpty()) {
            throw scanner.error("Expected token.");
        }
        return buffer.toString();
    }

    // =========================================================================
    // Tokens: URL
    // =========================================================================

    /**
     * Consumes a {@code url()} token if possible, and returns {@code null} otherwise.
     */
    protected String tryUrl() {
        var start = scanner.getState();
        if (!scanIdentifier("url")) return null;

        if (!scanner.scanChar($lparen)) {
            scanner.setState(start);
            return null;
        }

        whitespace(true);

        // Match Ruby Sass's behavior: parse a raw URL() if possible, and if not
        // backtrack and re-parse as a function expression.
        var buffer = new StringBuilder("url(");
        while (true) {
            int next = scanner.peekChar();
            if (next == -1) {
                break;
            } else if (next == $backslash) {
                buffer.append(escape(false));
            } else if (next == $percent
                    || next == $ampersand
                    || next == $hash
                    || (next >= $asterisk && next <= $tilde)
                    || next >= 0x0080) {
                buffer.appendCodePoint(scanner.readChar());
            } else if (isWhitespace(next)) {
                whitespace(true);
                if (scanner.peekChar() != $rparen) break;
            } else if (next == $rparen) {
                buffer.appendCodePoint(scanner.readChar());
                return buffer.toString();
            } else {
                break;
            }
        }

        scanner.setState(start);
        return null;
    }

    // =========================================================================
    // Characters: Escapes
    // =========================================================================

    /**
     * Consumes an escape sequence and returns the text that defines it.
     *
     * @param identifierStart if true, normalizes the escape as though it were at
     *                        the beginning of an identifier
     */
    protected String escape(boolean identifierStart) {
        // See https://drafts.csswg.org/css-syntax-3/#consume-escaped-code-point.

        int start = scanner.getPosition();
        scanner.expectChar($backslash);
        int next = scanner.peekChar();
        if (next == -1 || isNewline(next)) {
            throw scanner.error("Expected escape sequence.");
        }

        int value;
        if (isHexDigit(next)) {
            value = 0;
            for (int i = 0; i < 6; i++) {
                int ch = scanner.peekChar();
                if (ch == -1 || !isHexDigit(ch)) break;
                value *= 16;
                value += asHex(scanner.readChar());
            }
            scanCharIf(Characters::isWhitespace);
        } else {
            value = scanner.readChar();
        }

        if (identifierStart ? isNameStart(value) : isName(value)) {
            try {
                return new String(Character.toChars(value));
            } catch (IllegalArgumentException e) {
                throw scanner.error(
                        "Invalid Unicode code point.",
                        start, scanner.getPosition() - start);
            }
        } else if (value <= 0x1F || value == 0x7F
                || (identifierStart && isDigit(value))) {
            var buf = new StringBuilder();
            buf.append((char) $backslash);
            if (value > 0xF) buf.append(hexCharFor(value >> 4));
            buf.append(hexCharFor(value & 0xF));
            buf.append((char) $space);
            return buf.toString();
        } else {
            return new String(new int[]{$backslash, value}, 0, 2);
        }
    }

    /**
     * Consumes an escape sequence and returns the character (code point) it
     * represents.
     */
    protected int escapeCharacter() {
        // See https://drafts.csswg.org/css-syntax-3/#consume-escaped-code-point.

        scanner.expectChar($backslash);
        int next = scanner.peekChar();
        if (next == -1) {
            return 0xFFFD;
        } else if (isNewline(next)) {
            throw scanner.error("Expected escape sequence.");
        } else if (isHexDigit(next)) {
            int value = 0;
            for (int i = 0; i < 6; i++) {
                int ch = scanner.peekChar();
                if (ch == -1 || !isHexDigit(ch)) break;
                value = (value << 4) + asHex(scanner.readChar());
            }
            if (scanner.peekChar() != -1 && isWhitespace(scanner.peekChar())) {
                scanner.readChar();
            }

            if (value == 0
                    || (value >= 0xD800 && value <= 0xDFFF)
                    || value >= 0x10FFFF) {
                return 0xFFFD;
            }
            return value;
        } else {
            return scanner.readChar();
        }
    }

    /**
     * Consumes the next character if it matches {@code predicate}.
     *
     * @return whether the character was consumed
     */
    protected boolean scanCharIf(CharPredicate predicate) {
        int next = scanner.peekChar();
        if (next == -1 || !predicate.test(next)) return false;
        scanner.readChar();
        return true;
    }

    // =========================================================================
    // Lookahead
    // =========================================================================

    /**
     * Returns whether the scanner is immediately before a number.
     *
     * <p>This follows the CSS algorithm for "starts with a number".</p>
     */
    protected boolean lookingAtNumber() {
        int first = scanner.peekChar();
        if (first == -1) return false;
        if (isDigit(first)) return true;
        if (first == $dot) {
            int second = scanner.peekChar(1);
            return second != -1 && isDigit(second);
        }
        if (first == $plus || first == $minus) {
            int second = scanner.peekChar(1);
            if (second != -1 && isDigit(second)) return true;
            if (second == $dot) {
                int third = scanner.peekChar(2);
                return third != -1 && isDigit(third);
            }
        }
        return false;
    }

    /**
     * Returns whether the scanner is immediately before a SassScript expression.
     */
    protected boolean lookingAtExpression() {
        int next = scanner.peekChar();
        if (next == -1) return false;
        if (next == $dot) return scanner.peekChar(1) != $dot;
        if (next == $exclamation) {
            int second = scanner.peekChar(1);
            return second == -1 || second == $i || second == $I || isWhitespace(second);
        }
        return next == $lparen
                || next == $slash
                || next == $lbracket
                || next == $singleQuote
                || next == $doubleQuote
                || next == $hash
                || next == $plus
                || next == $minus
                || next == $backslash
                || next == $dollar
                || next == $ampersand
                || next == $percent
                || isNameStart(next)
                || isDigit(next);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Runs {@code consumer} and returns the source text that it consumes.
     */
    protected String rawText(Runnable consumer) {
        int start = scanner.getPosition();
        consumer.run();
        return scanner.substring(start);
    }

    /**
     * Creates a {@link FileSpan} from a previously saved scanner state to the
     * current position.
     */
    protected FileSpan spanFrom(SpanScanner.ScannerState start) {
        return scanner.spanFrom(start);
    }

    /**
     * Creates a {@link FileSpan} from one saved scanner state to another.
     */
    protected FileSpan spanFrom(SpanScanner.ScannerState start, SpanScanner.ScannerState end) {
        return scanner.spanFrom(start, end);
    }

    /**
     * Creates a {@link SassFormatException} associated with {@code span}.
     */
    protected SassFormatException error(String message, FileSpan span) {
        return new SassFormatException(message, span);
    }

    /**
     * Runs {@code callback} and wraps any {@link SassFormatException} it throws
     * in a properly formatted exception.
     *
     * <p>In the Dart version this catches SourceSpanFormatException and re-wraps
     * it. In this Java port, SpanScanner already throws SassFormatException,
     * so this primarily serves as a consistent boundary. Subclasses may override
     * for additional exception mapping.</p>
     */
    protected <T> T wrapSpanFormatException(Supplier<T> callback) {
        try {
            return callback.get();
        } catch (SassFormatException e) {
            // Already the right type; re-throw as-is.
            throw e;
        }
    }

    /**
     * Void variant of {@link #wrapSpanFormatException(Supplier)} for callbacks
     * that don't return a value.
     */
    protected void wrapSpanFormatException(Runnable callback) {
        wrapSpanFormatException(() -> {
            callback.run();
            return null;
        });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Helper for case-sensitive or case-insensitive character comparison.
     */
    private static boolean matches(int expected, int actual, boolean caseSensitive) {
        if (caseSensitive) {
            return actual == expected;
        }
        return characterEqualsIgnoreCase(expected, actual);
    }

    // =========================================================================
    // Inner class for static parse methods
    // =========================================================================

    /**
     * A minimal concrete Parser used only by the static utility methods
     * {@link #parseIdentifier} and {@link #isIdentifier}.
     */
    private static final class InlineParser extends Parser {
        InlineParser(String text) {
            super(text);
        }

        String doParseIdentifier() {
            return wrapSpanFormatException(() -> {
                String result = identifier();
                if (!scanner.isDone()) {
                    throw scanner.error("Expected end of input.");
                }
                return result;
            });
        }

        boolean doIsVariableDeclarationLike() {
            if (!scanner.scanChar($dollar)) return false;
            if (!lookingAtIdentifier()) return false;
            identifier();
            whitespace(true);
            return scanner.scanChar($colon);
        }
    }
}
