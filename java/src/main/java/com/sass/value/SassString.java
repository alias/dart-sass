package com.sass.value;

import com.sass.exception.SassScriptException;
import com.sass.util.Characters;
import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A SassScript string.
 * <p>
 * Strings can be either quoted or unquoted. Unquoted strings are usually CSS
 * identifiers, but may contain any text.
 */
public final class SassString extends Value {
    private static final SassString EMPTY_QUOTED = new SassString("", true);
    private static final SassString EMPTY_UNQUOTED = new SassString("", false);

    private final String text;
    private final boolean hasQuotes;
    private int sassLength = -1; // lazily computed
    private int hashCache;
    private boolean hashComputed;

    /** Creates a string with the given text. */
    public SassString(String text, boolean quotes) {
        this.text = text;
        this.hasQuotes = quotes;
    }

    /** Creates a quoted string. */
    public SassString(String text) {
        this(text, true);
    }

    /** Returns an empty string. */
    public static SassString empty(boolean quotes) {
        return quotes ? EMPTY_QUOTED : EMPTY_UNQUOTED;
    }

    /** The contents of the string. */
    public String getText() { return text; }

    /** Whether this string has quotes. */
    public boolean hasQuotes() { return hasQuotes; }

    /**
     * Sass's notion of the length of this string.
     * Sass treats strings as Unicode code points, while Java uses UTF-16 code units.
     */
    public int sassLength() {
        if (sassLength == -1) {
            sassLength = (int) text.codePoints().count();
        }
        return sassLength;
    }

    @Override
    public boolean isBlank() { return !hasQuotes && text.isEmpty(); }

    @Override
    public boolean isSpecialNumber() {
        if (hasQuotes) return false;
        if (text.length() < 6) return false; // "min(_)".length()
        int c0 = text.charAt(0);
        return switch (Characters.toLowerCase(c0)) {
            case 'a' -> text.length() >= 5 &&
                    Characters.equalsLetterIgnoreCase('t', text.charAt(1)) &&
                    Characters.equalsLetterIgnoreCase('t', text.charAt(2)) &&
                    Characters.equalsLetterIgnoreCase('r', text.charAt(3)) &&
                    text.charAt(4) == '(';
            case 'c' -> {
                int c1 = Characters.toLowerCase(text.charAt(1));
                if (c1 == 'l') {
                    yield text.length() >= 6 &&
                            Characters.equalsLetterIgnoreCase('a', text.charAt(2)) &&
                            Characters.equalsLetterIgnoreCase('m', text.charAt(3)) &&
                            Characters.equalsLetterIgnoreCase('p', text.charAt(4)) &&
                            text.charAt(5) == '(';
                } else if (c1 == 'a') {
                    yield text.length() >= 5 &&
                            Characters.equalsLetterIgnoreCase('l', text.charAt(2)) &&
                            Characters.equalsLetterIgnoreCase('c', text.charAt(3)) &&
                            text.charAt(4) == '(';
                }
                yield false;
            }
            case 'v' -> Characters.equalsLetterIgnoreCase('a', text.charAt(1)) &&
                    Characters.equalsLetterIgnoreCase('r', text.charAt(2)) &&
                    text.charAt(3) == '(';
            case 'e' -> Characters.equalsLetterIgnoreCase('n', text.charAt(1)) &&
                    Characters.equalsLetterIgnoreCase('v', text.charAt(2)) &&
                    text.charAt(3) == '(';
            case 'm' -> {
                int c1 = Characters.toLowerCase(text.charAt(1));
                if (c1 == 'a') {
                    yield Characters.equalsLetterIgnoreCase('x', text.charAt(2)) &&
                            text.charAt(3) == '(';
                } else if (c1 == 'i') {
                    yield Characters.equalsLetterIgnoreCase('n', text.charAt(2)) &&
                            text.charAt(3) == '(';
                }
                yield false;
            }
            case 'i' -> Characters.equalsLetterIgnoreCase('f', text.charAt(1)) &&
                    text.charAt(2) == '(';
            default -> false;
        };
    }

    @Override
    public boolean isSpecialVariable() {
        if (hasQuotes) return false;
        if (text.length() < 6) return false; // "var(_)".length()
        int c0 = Characters.toLowerCase(text.charAt(0));
        return switch (c0) {
            case 'v' -> Characters.equalsLetterIgnoreCase('a', text.charAt(1)) &&
                    Characters.equalsLetterIgnoreCase('r', text.charAt(2)) &&
                    text.charAt(3) == '(';
            case 'i' -> Characters.equalsLetterIgnoreCase('f', text.charAt(1)) &&
                    text.charAt(2) == '(';
            case 'a' -> Characters.equalsLetterIgnoreCase('t', text.charAt(1)) &&
                    Characters.equalsLetterIgnoreCase('t', text.charAt(2)) &&
                    Characters.equalsLetterIgnoreCase('r', text.charAt(3)) &&
                    text.charAt(4) == '(';
            default -> false;
        };
    }

    /**
     * Converts a Sass string index to a Java string index.
     * Handles 1-based indexing and negative indices.
     */
    public int sassIndexToStringIndex(Value sassIndex, @Nullable String name) {
        int runeIndex = sassIndexToRuneIndex(sassIndex, name);
        return codepointIndexToCodeUnitIndex(text, runeIndex);
    }

    /**
     * Converts a Sass string index to a codepoint index.
     */
    public int sassIndexToRuneIndex(Value sassIndex, @Nullable String name) {
        int index = sassIndex.assertNumber(name).assertInt(name);
        if (index == 0) {
            throw new SassScriptException("String index may not be 0.", name);
        }
        if (Math.abs(index) > sassLength()) {
            throw new SassScriptException(
                    "Invalid index " + sassIndex + " for a string with " + sassLength() + " characters.",
                    name);
        }
        return index < 0 ? sassLength() + index : index - 1;
    }

    /** Converts a codepoint index to a code unit (char) index. */
    private static int codepointIndexToCodeUnitIndex(String text, int codepointIndex) {
        int codeUnitIndex = 0;
        for (int i = 0; i < codepointIndex; i++) {
            if (codeUnitIndex >= text.length()) break;
            if (Character.isHighSurrogate(text.charAt(codeUnitIndex))) {
                codeUnitIndex += 2;
            } else {
                codeUnitIndex++;
            }
        }
        return codeUnitIndex;
    }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitString(this);
    }

    @Override
    public SassString assertString(@Nullable String name) { return this; }

    @Override
    public Value plus(Value other) {
        if (other instanceof SassString s) {
            return new SassString(text + s.text, hasQuotes);
        }
        return new SassString(text + other.toCssString(), hasQuotes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassString s && text.equals(s.text);
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            hashCache = text.hashCode();
            hashComputed = true;
        }
        return hashCache;
    }

    @Override
    public String toString() {
        if (hasQuotes) {
            // Simplified — full implementation uses the serializer
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return text;
    }
}
