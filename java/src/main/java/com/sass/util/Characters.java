package com.sass.util;

/**
 * Character classification utilities for Sass parsing.
 */
public final class Characters {
    private Characters() {}

    /** Returns whether {@code c} is an ASCII whitespace character. */
    public static boolean isWhitespace(int c) {
        return c == 0x20 || c == 0x09 || c == 0x0A || c == 0x0D || c == 0x0C;
    }

    /** Returns whether {@code c} is a newline character. */
    public static boolean isNewline(int c) {
        return c == 0x0A || c == 0x0D || c == 0x0C;
    }

    /** Returns whether {@code c} is an ASCII alphabetic character. */
    public static boolean isAlphabetic(int c) {
        return (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A);
    }

    /** Returns whether {@code c} is an ASCII digit. */
    public static boolean isDigit(int c) {
        return c >= 0x30 && c <= 0x39;
    }

    /** Returns whether {@code c} is an ASCII hex digit. */
    public static boolean isHexDigit(int c) {
        return isDigit(c) || (c >= 0x41 && c <= 0x46) || (c >= 0x61 && c <= 0x66);
    }

    /** Returns whether {@code c} can start a CSS identifier. */
    public static boolean isNameStart(int c) {
        return isAlphabetic(c) || c == 0x5F || c >= 0x80;
    }

    /** Returns whether {@code c} can appear in a CSS identifier after the first character. */
    public static boolean isName(int c) {
        return isNameStart(c) || isDigit(c) || c == 0x2D;
    }

    /** Returns the hex value of {@code c}, which must be a hex digit. */
    public static int asHex(int c) {
        if (c <= 0x39) return c - 0x30;
        if (c <= 0x46) return 10 + c - 0x41;
        return 10 + c - 0x61;
    }

    /** Returns the hex digit character for a value 0-15. */
    public static char hexCharFor(int value) {
        return (char) (value < 10 ? 0x30 + value : 0x61 + value - 10);
    }

    /**
     * Returns whether {@code actual} is the same letter as {@code expected},
     * case-insensitively. {@code expected} must be a lowercase letter.
     */
    public static boolean equalsLetterIgnoreCase(int expected, int actual) {
        return actual == expected || actual == expected - 32;
    }

    /** Converts a character to lowercase if it's an uppercase ASCII letter. */
    public static int toLowerCase(int c) {
        return (c >= 0x41 && c <= 0x5A) ? c + 32 : c;
    }

    /** Converts a character to uppercase if it's a lowercase ASCII letter. */
    public static int toUpperCase(int c) {
        return (c >= 0x61 && c <= 0x7A) ? c - 32 : c;
    }

    /** Returns whether {@code c} is a space or tab character. */
    public static boolean isSpaceOrTab(int c) {
        return c == 0x20 || c == 0x09;
    }

    /**
     * Returns whether two characters are the same, modulo ASCII case.
     */
    public static boolean characterEqualsIgnoreCase(int character1, int character2) {
        if (character1 == character2) return true;
        // If this check fails, the characters are definitely different. If it
        // succeeds *and* either character is an ASCII letter, they're equivalent.
        if ((character1 ^ character2) != 0x20) return false;
        // Now we just need to verify that one of the characters is an ASCII letter.
        int upperCase1 = character1 & ~0x20;
        return upperCase1 >= 0x41 && upperCase1 <= 0x5A;
    }

    /** Returns the value of {@code c} as a decimal digit. Assumes c is a digit. */
    public static int asDecimal(int c) {
        return c - 0x30;
    }

    /**
     * Returns the right-hand bracket for a left-hand bracket character.
     */
    public static int opposite(int character) {
        return switch (character) {
            case 0x28 -> 0x29; // ( -> )
            case 0x7B -> 0x7D; // { -> }
            case 0x5B -> 0x5D; // [ -> ]
            default -> throw new IllegalArgumentException(
                    "'" + (char) character + "' isn't a brace-like character.");
        };
    }
}
