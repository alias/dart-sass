package com.sass.parse;

import com.sass.ast.css.CssMediaQuery;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.sass.parse.CharCodes.*;
import static com.sass.util.Characters.*;

/**
 * A parser for {@code @media} queries.
 *
 * <p>Port of Dart sass media_query.dart parser.</p>
 */
public class MediaQueryParser extends Parser {

    /**
     * Creates a media query parser.
     *
     * @param contents the media query text to parse
     * @param url the source URL for error reporting (may be null)
     */
    public MediaQueryParser(String contents, URI url) {
        super(contents, url);
    }

    /**
     * Creates a media query parser with no URL.
     *
     * @param contents the media query text to parse
     */
    public MediaQueryParser(String contents) {
        this(contents, null);
    }

    /**
     * Parses a comma-separated list of media queries.
     *
     * @return the list of parsed media queries
     */
    public List<CssMediaQuery> parse() {
        return wrapSpanFormatException(() -> {
            var queries = new ArrayList<CssMediaQuery>();
            do {
                consumeWhitespace();
                queries.add(mediaQuery());
                consumeWhitespace();
            } while (scanner.scanChar($comma));
            scanner.expectDone();
            return queries;
        });
    }

    /**
     * Consumes a single media query.
     */
    private CssMediaQuery mediaQuery() {
        // This is somewhat duplicated in StylesheetParser._mediaQuery.
        if (scanner.peekChar() == $lparen) {
            var conditions = new ArrayList<String>();
            conditions.add(mediaInParens());
            consumeWhitespace();

            boolean conjunction = true;
            if (scanIdentifier("and")) {
                expectWhitespace(false);
                conditions.addAll(mediaLogicSequence("and"));
            } else if (scanIdentifier("or")) {
                expectWhitespace(false);
                conjunction = false;
                conditions.addAll(mediaLogicSequence("or"));
            }

            return new CssMediaQuery(null, null, conditions, conjunction);
        }

        String modifier = null;
        String type;
        var identifier1 = identifier();

        if (equalsIgnoreCase(identifier1, "not")) {
            expectWhitespace(false);
            if (!lookingAtIdentifier()) {
                // For example, "@media not (...) {"
                var conditions = new ArrayList<String>();
                conditions.add("(not " + mediaInParens() + ")");
                return new CssMediaQuery(null, null, conditions, true);
            }
        }

        consumeWhitespace();
        if (!lookingAtIdentifier()) {
            // For example, "@media screen {"
            return new CssMediaQuery(null, identifier1, List.of(), true);
        }

        var identifier2 = identifier();

        if (equalsIgnoreCase(identifier2, "and")) {
            expectWhitespace(false);
            // For example, "@media screen and ..."
            type = identifier1;
        } else {
            consumeWhitespace();
            modifier = identifier1;
            type = identifier2;
            if (scanIdentifier("and")) {
                // For example, "@media only screen and ..."
                expectWhitespace(false);
            } else {
                // For example, "@media only screen {"
                return new CssMediaQuery(modifier, type, List.of(), true);
            }
        }

        // We've consumed either `IDENTIFIER "and"` or
        // `IDENTIFIER IDENTIFIER "and"`.

        if (scanIdentifier("not")) {
            // For example, "@media screen and not (...) {"
            expectWhitespace(false);
            var conditions = new ArrayList<String>();
            conditions.add("(not " + mediaInParens() + ")");
            return new CssMediaQuery(modifier, type, conditions, true);
        }

        return new CssMediaQuery(modifier, type, mediaLogicSequence("and"), true);
    }

    /**
     * Consumes one or more {@code <media-in-parens>} expressions separated by
     * the given operator and returns them.
     *
     * @param operator the operator that separates expressions (e.g., "and" or "or")
     */
    private List<String> mediaLogicSequence(String operator) {
        var result = new ArrayList<String>();
        while (true) {
            result.add(mediaInParens());
            consumeWhitespace();

            if (!scanIdentifier(operator)) return result;
            expectWhitespace(false);
        }
    }

    /**
     * Consumes a {@code <media-in-parens>} expression and returns it,
     * parentheses included.
     */
    private String mediaInParens() {
        scanner.expectChar($lparen, "media condition in parentheses");
        var result = "(" + declarationValue(false) + ")";
        scanner.expectChar($rparen);
        return result;
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
     * Returns whether the two strings are equal, case-insensitively.
     */
    private static boolean equalsIgnoreCase(String s1, String s2) {
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
