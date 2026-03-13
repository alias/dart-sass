package com.sass.util;

/**
 * Utilities for fuzzy floating-point comparison.
 * <p>
 * Sass uses fuzzy equality for numbers: two numbers are considered equal if
 * they differ by less than a small epsilon value. This matches the behavior
 * of the reference dart-sass implementation.
 */
public final class FuzzyMath {
    /** The epsilon value used for fuzzy comparisons. */
    private static final double EPSILON = 1e-11;

    private FuzzyMath() {}

    /** Returns whether {@code a} and {@code b} are equal within epsilon. */
    public static boolean fuzzyEquals(double a, double b) {
        if (a == b) return true;
        return Math.abs(a - b) < EPSILON;
    }

    /** Returns a hash code for {@code value} that matches fuzzy equality. */
    public static int fuzzyHashCode(double value) {
        if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY || Double.isNaN(value)) {
            return Double.hashCode(value);
        }
        return Double.hashCode(fuzzyRound(value * 1e10) / 1e10);
    }

    /** Returns whether {@code number} is an integer within epsilon. */
    public static boolean fuzzyIsInt(double number) {
        if (Double.isInfinite(number) || Double.isNaN(number)) return false;
        return fuzzyEquals(number, Math.round(number));
    }

    /** Returns {@code number} as an int if it's fuzzy-equal to an integer, or null otherwise. */
    public static Integer fuzzyAsInt(double number) {
        if (Double.isInfinite(number) || Double.isNaN(number)) return null;
        if (!fuzzyIsInt(number)) return null;
        return (int) Math.round(number);
    }

    /** Rounds {@code number} to the nearest integer, with ties going away from zero. */
    public static double fuzzyRound(double number) {
        if (number > 0) {
            return fuzzyIsInt(number - 0.5) ? Math.ceil(number - 0.5) : Math.round(number);
        } else {
            return fuzzyIsInt(number + 0.5) ? Math.floor(number + 0.5) : Math.round(number);
        }
    }

    /** Returns the integer value of {@code number} if it's fuzzy-equal to an integer, or throws. */
    public static int fuzzyCheckInt(double number) {
        Integer asInt = fuzzyAsInt(number);
        if (asInt != null) return asInt;
        throw new ArithmeticException(number + " is not an integer.");
    }

    /** Returns whether {@code a > b}, considering fuzzy equality. */
    public static boolean fuzzyGreaterThan(double a, double b) {
        return a > b && !fuzzyEquals(a, b);
    }

    /** Returns whether {@code a >= b}, considering fuzzy equality. */
    public static boolean fuzzyGreaterThanOrEquals(double a, double b) {
        return a > b || fuzzyEquals(a, b);
    }

    /** Returns whether {@code a < b}, considering fuzzy equality. */
    public static boolean fuzzyLessThan(double a, double b) {
        return a < b && !fuzzyEquals(a, b);
    }

    /** Returns whether {@code a <= b}, considering fuzzy equality. */
    public static boolean fuzzyLessThanOrEquals(double a, double b) {
        return a < b || fuzzyEquals(a, b);
    }

    /** Clamps {@code value} between {@code min} and {@code max}, using fuzzy comparison. */
    public static double fuzzyClamp(double value, double min, double max) {
        if (fuzzyLessThanOrEquals(value, min)) return min;
        if (fuzzyGreaterThanOrEquals(value, max)) return max;
        return value;
    }

    /** Returns whether {@code value} is between 0 and {@code max}, inclusive, using fuzzy comparison. */
    public static boolean fuzzyInRange(double value, double min, double max) {
        return fuzzyGreaterThanOrEquals(value, min) && fuzzyLessThanOrEquals(value, max);
    }

    /**
     * Asserts that {@code value} is within the range {@code [min, max]} using fuzzy comparison.
     * Returns the clamped value.
     */
    public static double fuzzyAssertRange(double value, double min, double max, String name) {
        if (fuzzyInRange(value, min, max)) return fuzzyClamp(value, min, max);
        throw new IllegalArgumentException(
                "Expected " + name + " to be within " + min + " and " + max + ", was " + value + ".");
    }
}
