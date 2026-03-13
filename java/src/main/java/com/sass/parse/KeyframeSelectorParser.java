package com.sass.parse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.sass.parse.CharCodes.*;
import static com.sass.util.Characters.*;

/**
 * A parser for {@code @keyframes} block selectors.
 *
 * <p>Keyframe selectors are comma-separated values that can be either
 * "from", "to", or a percentage (e.g., "50%", "100%").</p>
 *
 * <p>Port of Dart sass keyframe_selector.dart parser.</p>
 */
public class KeyframeSelectorParser extends Parser {

    /**
     * Creates a keyframe selector parser.
     *
     * @param contents the keyframe selector text to parse
     * @param url the source URL for error reporting (may be null)
     */
    public KeyframeSelectorParser(String contents, URI url) {
        super(contents, url);
    }

    /**
     * Creates a keyframe selector parser with no URL.
     *
     * @param contents the keyframe selector text to parse
     */
    public KeyframeSelectorParser(String contents) {
        this(contents, null);
    }

    /**
     * Parses a comma-separated list of keyframe selectors.
     *
     * @return the list of parsed keyframe selectors
     */
    public List<String> parse() {
        return wrapSpanFormatException(() -> {
            var selectors = new ArrayList<String>();
            do {
                consumeWhitespace();
                if (lookingAtIdentifier()) {
                    if (scanIdentifier("from")) {
                        selectors.add("from");
                    } else {
                        expectIdentifier("to", "\"to\" or \"from\"", false);
                        selectors.add("to");
                    }
                } else {
                    selectors.add(percentage());
                }
                consumeWhitespace();
            } while (scanner.scanChar($comma));
            scanner.expectDone();
            return selectors;
        });
    }

    /**
     * Consumes a percentage value (e.g., "50%", "100%", "12.5%").
     * Supports optional leading {@code +}, decimal points, and scientific notation.
     *
     * @return the percentage string including the trailing '%'
     */
    private String percentage() {
        var buffer = new StringBuilder();
        if (scanner.scanChar($plus)) {
            buffer.append('+');
        }

        int second = scanner.peekChar();
        if (!isDigit(second) && second != $dot) {
            throw scanner.error("Expected number.");
        }

        while (isDigit(scanner.peekChar())) {
            buffer.appendCodePoint(scanner.readChar());
        }

        if (scanner.peekChar() == $dot) {
            buffer.appendCodePoint(scanner.readChar());

            while (isDigit(scanner.peekChar())) {
                buffer.appendCodePoint(scanner.readChar());
            }
        }

        if (scanIdentChar($e)) {
            buffer.append('e');
            int next = scanner.peekChar();
            if (next == $plus || next == $minus) {
                buffer.appendCodePoint(scanner.readChar());
            }
            if (!isDigit(scanner.peekChar())) {
                throw scanner.error("Expected digit.");
            }
            do {
                buffer.appendCodePoint(scanner.readChar());
            } while (isDigit(scanner.peekChar()));
        }

        scanner.expectChar($percent);
        buffer.append('%');
        return buffer.toString();
    }

    /**
     * Consumes whitespace. The value of consumeNewlines is not relevant for
     * this class, so we always pass true.
     */
    private void consumeWhitespace() {
        whitespace(true);
    }
}
