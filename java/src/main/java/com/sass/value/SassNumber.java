package com.sass.value;

import com.sass.exception.SassScriptException;
import com.sass.util.FuzzyMath;
import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A SassScript number, optionally with units.
 * <p>
 * Numbers can have numerator and denominator units (e.g., pixels/second).
 * This is the abstract base; concrete subclasses are UnitlessNumber,
 * SingleUnitNumber, and ComplexNumber.
 */
public abstract class SassNumber extends Value {
    /** The number of decimal digits emitted when converting to CSS. */
    public static final int PRECISION = 10;

    private static final double EPSILON = Math.pow(10, -PRECISION - 1);

    // Unit conversion tables
    private static final Map<String, Map<String, Double>> CONVERSIONS = Map.ofEntries(
        // Length
        Map.entry("in", Map.of("in", 1.0, "cm", 1/2.54, "pc", 1.0/6, "mm", 1/25.4, "q", 1/101.6, "pt", 1.0/72, "px", 1.0/96)),
        Map.entry("cm", Map.of("in", 2.54, "cm", 1.0, "pc", 2.54/6, "mm", 0.1, "q", 0.025, "pt", 2.54/72, "px", 2.54/96)),
        Map.entry("pc", Map.of("in", 6.0, "cm", 6/2.54, "pc", 1.0, "mm", 6/25.4, "q", 6/101.6, "pt", 1.0/12, "px", 1.0/16)),
        Map.entry("mm", Map.of("in", 25.4, "cm", 10.0, "pc", 25.4/6, "mm", 1.0, "q", 0.25, "pt", 25.4/72, "px", 25.4/96)),
        Map.entry("q",  Map.of("in", 101.6, "cm", 40.0, "pc", 101.6/6, "mm", 4.0, "q", 1.0, "pt", 101.6/72, "px", 101.6/96)),
        Map.entry("pt", Map.of("in", 72.0, "cm", 72/2.54, "pc", 12.0, "mm", 72/25.4, "q", 72/101.6, "pt", 1.0, "px", 0.75)),
        Map.entry("px", Map.of("in", 96.0, "cm", 96/2.54, "pc", 16.0, "mm", 96/25.4, "q", 96/101.6, "pt", 4.0/3, "px", 1.0)),
        // Angle
        Map.entry("deg",  Map.of("deg", 1.0, "grad", 0.9, "rad", 180/Math.PI, "turn", 360.0)),
        Map.entry("grad", Map.of("deg", 10.0/9, "grad", 1.0, "rad", 200/Math.PI, "turn", 400.0)),
        Map.entry("rad",  Map.of("deg", Math.PI/180, "grad", Math.PI/200, "rad", 1.0, "turn", 2*Math.PI)),
        Map.entry("turn", Map.of("deg", 1.0/360, "grad", 1.0/400, "rad", 1/(2*Math.PI), "turn", 1.0)),
        // Time
        Map.entry("s",  Map.of("s", 1.0, "ms", 0.001)),
        Map.entry("ms", Map.of("s", 1000.0, "ms", 1.0)),
        // Frequency
        Map.entry("Hz",  Map.of("Hz", 1.0, "kHz", 1000.0)),
        Map.entry("kHz", Map.of("Hz", 0.001, "kHz", 1.0)),
        // Pixel density
        Map.entry("dpi",  Map.of("dpi", 1.0, "dpcm", 2.54, "dppx", 96.0)),
        Map.entry("dpcm", Map.of("dpi", 1/2.54, "dpcm", 1.0, "dppx", 96/2.54)),
        Map.entry("dppx", Map.of("dpi", 1.0/96, "dpcm", 2.54/96, "dppx", 1.0))
    );

    private static final Map<String, String> TYPES_BY_UNIT = Map.ofEntries(
        Map.entry("in", "length"), Map.entry("cm", "length"), Map.entry("pc", "length"),
        Map.entry("mm", "length"), Map.entry("q", "length"), Map.entry("pt", "length"),
        Map.entry("px", "length"),
        Map.entry("deg", "angle"), Map.entry("grad", "angle"),
        Map.entry("rad", "angle"), Map.entry("turn", "angle"),
        Map.entry("s", "time"), Map.entry("ms", "time"),
        Map.entry("Hz", "frequency"), Map.entry("kHz", "frequency"),
        Map.entry("dpi", "pixel density"), Map.entry("dpcm", "pixel density"),
        Map.entry("dppx", "pixel density")
    );

    private final double value;
    protected @Nullable Integer hashCache;

    protected SassNumber(double value) {
        this.value = value;
    }

    // -- Factory methods --

    /** Creates a unitless number. */
    public static SassNumber create(double value) {
        return new UnitlessNumber(value);
    }

    /** Creates a unitless number. Alias for {@link #create(double)}. */
    public static SassNumber unitless(double value) {
        return new UnitlessNumber(value);
    }

    /** Creates a number with a single unit. */
    public static SassNumber create(double value, String unit) {
        return new SingleUnitNumber(value, unit);
    }

    /** Creates a number with numerator and denominator units. */
    public static SassNumber withUnits(double value, List<String> numeratorUnits, List<String> denominatorUnits) {
        if (numeratorUnits.isEmpty() && denominatorUnits.isEmpty()) {
            return new UnitlessNumber(value);
        }
        if (numeratorUnits.size() == 1 && denominatorUnits.isEmpty()) {
            return new SingleUnitNumber(value, numeratorUnits.get(0));
        }
        return new ComplexNumber(value, numeratorUnits, denominatorUnits);
    }

    // -- Properties --

    /** The numeric value. */
    public double getValue() { return value; }

    /** This number's numerator units. */
    public abstract List<String> getNumeratorUnits();

    /** This number's denominator units. */
    public abstract List<String> getDenominatorUnits();

    /** Whether this has any units. */
    public abstract boolean hasUnits();

    /** Whether this has complex (multi-unit) units. */
    public boolean hasComplexUnits() {
        return getNumeratorUnits().size() > 1 || !getDenominatorUnits().isEmpty();
    }

    /** Whether this is an integer. */
    public boolean isInt() { return FuzzyMath.fuzzyIsInt(value); }

    /** Returns this as an int if it's fuzzy-equal to one, or null. */
    public @Nullable Integer asInt() { return FuzzyMath.fuzzyAsInt(value); }

    /**
     * Asserts this is an integer and returns its value.
     */
    public int assertInt(@Nullable String name) {
        Integer i = asInt();
        if (i != null) return i;
        throw new SassScriptException(this + " is not an int.", name);
    }

    public int assertInt() { return assertInt(null); }

    /** Asserts this has no units. */
    public SassNumber assertNoUnits(@Nullable String name) {
        if (!hasUnits()) return this;
        throw new SassScriptException("Expected " + this + " to have no units.", name);
    }

    public SassNumber assertNoUnits() { return assertNoUnits(null); }

    /** Asserts this has the given unit. */
    public SassNumber assertUnit(String unit, @Nullable String name) {
        if (hasUnit(unit)) return this;
        throw new SassScriptException("Expected " + this + " to have unit \"" + unit + "\".", name);
    }

    public SassNumber assertUnit(String unit) { return assertUnit(unit, null); }

    /** Whether this has exactly the given unit. */
    public boolean hasUnit(String unit) {
        return getNumeratorUnits().size() == 1 &&
                getDenominatorUnits().isEmpty() &&
                getNumeratorUnits().get(0).equals(unit);
    }

    /** Whether this number's units are compatible with {@code other}'s. */
    public boolean compatibleWithUnit(String unit) {
        if (!hasUnits()) return true;
        if (getNumeratorUnits().size() != 1 || !getDenominatorUnits().isEmpty()) return false;
        return conversionFactor(getNumeratorUnits().get(0), unit) != null;
    }

    /** Converts this to the given unit, returning the numeric value. */
    public double coerceValueToUnit(String unit, @Nullable String name) {
        return coerceValueToUnits(List.of(unit), List.of(), name);
    }

    /** Converts this to the given units, returning the numeric value. */
    public double coerceValueToUnits(List<String> newNumerators, List<String> newDenominators, @Nullable String name) {
        return coerceOrConvertValue(newNumerators, newDenominators, false, name);
    }

    /** Converts this number to new units. */
    public SassNumber coerceToUnits(List<String> newNumerators, List<String> newDenominators, @Nullable String name) {
        double newValue = coerceValueToUnits(newNumerators, newDenominators, name);
        return withUnits(newValue, newNumerators, newDenominators);
    }

    private double coerceOrConvertValue(List<String> newNumerators, List<String> newDenominators,
                                         boolean coerceUnitless, @Nullable String name) {
        if (listEquals(getNumeratorUnits(), newNumerators) && listEquals(getDenominatorUnits(), newDenominators)) {
            return value;
        }
        if (!hasUnits() || (newNumerators.isEmpty() && newDenominators.isEmpty())) {
            if (coerceUnitless) return value;
            // Allow unitless -> unit coercion
            return value;
        }

        double result = value;
        // Convert numerators
        var oldNumerators = new java.util.ArrayList<>(getNumeratorUnits());
        for (String newUnit : newNumerators) {
            boolean found = false;
            for (int i = 0; i < oldNumerators.size(); i++) {
                Double factor = conversionFactor(newUnit, oldNumerators.get(i));
                if (factor != null) {
                    result *= factor;
                    oldNumerators.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found && !oldNumerators.isEmpty()) {
                throwIncompatibleUnitsError(newNumerators, newDenominators, name);
            }
        }
        // Convert denominators
        var oldDenominators = new java.util.ArrayList<>(getDenominatorUnits());
        for (String newUnit : newDenominators) {
            boolean found = false;
            for (int i = 0; i < oldDenominators.size(); i++) {
                Double factor = conversionFactor(oldDenominators.get(i), newUnit);
                if (factor != null) {
                    result *= factor;
                    oldDenominators.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found && !oldDenominators.isEmpty()) {
                throwIncompatibleUnitsError(newNumerators, newDenominators, name);
            }
        }
        return result;
    }

    private void throwIncompatibleUnitsError(List<String> newNumerators, List<String> newDenominators, @Nullable String name) {
        throw new SassScriptException(
                "Incompatible units " + unitString(getNumeratorUnits(), getDenominatorUnits()) +
                " and " + unitString(newNumerators, newDenominators) + ".",
                name);
    }

    /** Returns the conversion factor from unit1 to unit2, or null if incompatible. */
    public static @Nullable Double conversionFactor(String unit1, String unit2) {
        if (unit1.equals(unit2)) return 1.0;
        var inner = CONVERSIONS.get(unit1);
        if (inner != null) return inner.get(unit2);
        return null;
    }

    /** Returns a human-readable unit string. */
    public String unitString() {
        return unitString(getNumeratorUnits(), getDenominatorUnits());
    }

    /** Returns a human-readable unit string for the given units. */
    public static String unitString(List<String> numerators, List<String> denominators) {
        if (numerators.isEmpty() && denominators.isEmpty()) return "no units";
        if (denominators.isEmpty()) return String.join("*", numerators);
        if (numerators.isEmpty()) return String.join("*", denominators) + "^-1";
        return String.join("*", numerators) + "/" + String.join("*", denominators);
    }

    /** Suggests a call to stripUnit or similar for deprecation messages. */
    public String unitSuggestion(String name) {
        return name + " * 1" + unitString();
    }

    // -- Arithmetic --

    @Override
    public SassNumber assertNumber(@Nullable String name) { return this; }

    @Override
    public Value plus(Value other) {
        if (other instanceof SassNumber n) {
            return withValue(value + n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null));
        }
        return super.plus(other);
    }

    @Override
    public Value minus(Value other) {
        if (other instanceof SassNumber n) {
            return withValue(value - n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null));
        }
        return super.minus(other);
    }

    @Override
    public Value times(Value other) {
        if (other instanceof SassNumber n) {
            var newNumerators = new java.util.ArrayList<>(getNumeratorUnits());
            newNumerators.addAll(n.getNumeratorUnits());
            var newDenominators = new java.util.ArrayList<>(getDenominatorUnits());
            newDenominators.addAll(n.getDenominatorUnits());
            return withUnits(value * n.value, newNumerators, newDenominators);
        }
        throw new SassScriptException("Undefined operation \"" + this + " * " + other + "\".");
    }

    @Override
    public Value modulo(Value other) {
        if (other instanceof SassNumber n) {
            double otherValue = n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null);
            double result = value % otherValue;
            // Sass modulo follows the sign of the dividend for Dart semantics
            return withValue(result);
        }
        throw new SassScriptException("Undefined operation \"" + this + " % " + other + "\".");
    }

    @Override
    public Value dividedBy(Value other) {
        if (other instanceof SassNumber n) {
            var newNumerators = new java.util.ArrayList<>(getNumeratorUnits());
            newNumerators.addAll(n.getDenominatorUnits());
            var newDenominators = new java.util.ArrayList<>(getDenominatorUnits());
            newDenominators.addAll(n.getNumeratorUnits());
            return withUnits(value / n.value, newNumerators, newDenominators);
        }
        return super.dividedBy(other);
    }

    @Override
    public Value unaryPlus() { return this; }

    @Override
    public Value unaryMinus() { return withValue(-value); }

    @Override
    public SassBoolean greaterThan(Value other) {
        if (other instanceof SassNumber n) {
            return SassBoolean.of(FuzzyMath.fuzzyGreaterThan(value,
                    n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null)));
        }
        return super.greaterThan(other);
    }

    @Override
    public SassBoolean greaterThanOrEquals(Value other) {
        if (other instanceof SassNumber n) {
            return SassBoolean.of(FuzzyMath.fuzzyGreaterThanOrEquals(value,
                    n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null)));
        }
        return super.greaterThanOrEquals(other);
    }

    @Override
    public SassBoolean lessThan(Value other) {
        if (other instanceof SassNumber n) {
            return SassBoolean.of(FuzzyMath.fuzzyLessThan(value,
                    n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null)));
        }
        return super.lessThan(other);
    }

    @Override
    public SassBoolean lessThanOrEquals(Value other) {
        if (other instanceof SassNumber n) {
            return SassBoolean.of(FuzzyMath.fuzzyLessThanOrEquals(value,
                    n.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null)));
        }
        return super.lessThanOrEquals(other);
    }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitNumber(this);
    }

    /** Returns a new number with the same units but a different value. */
    public abstract SassNumber withValue(double value);

    /**
     * Whether this number's units are compatible with another number's for
     * comparison and arithmetic purposes.
     */
    public boolean isComparableTo(SassNumber other) {
        if (!hasUnits() || !other.hasUnits()) return true;
        try {
            other.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null);
            return true;
        } catch (SassScriptException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SassNumber o)) return false;
        if (getNumeratorUnits().size() != o.getNumeratorUnits().size()) return false;
        if (getDenominatorUnits().size() != o.getDenominatorUnits().size()) return false;
        if (!hasUnits()) return FuzzyMath.fuzzyEquals(value, o.value);

        // Try to convert other to our units
        try {
            double otherValue = o.coerceValueToUnits(getNumeratorUnits(), getDenominatorUnits(), null);
            return FuzzyMath.fuzzyEquals(value, otherValue);
        } catch (SassScriptException e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        if (hashCache != null) return hashCache;
        hashCache = FuzzyMath.fuzzyHashCode(value * canonicalMultiplier());
        return hashCache;
    }

    /** Returns a multiplier that normalizes this number to canonical units. */
    private double canonicalMultiplier() {
        double multiplier = 1.0;
        for (String unit : getNumeratorUnits()) {
            multiplier *= canonicalMultiplierForUnit(unit);
        }
        for (String unit : getDenominatorUnits()) {
            multiplier /= canonicalMultiplierForUnit(unit);
        }
        return multiplier;
    }

    private static double canonicalMultiplierForUnit(String unit) {
        var inner = CONVERSIONS.get(unit);
        if (inner == null) return 1.0;
        // Convert to the canonical unit (first in the type group)
        String type = TYPES_BY_UNIT.get(unit);
        if (type == null) return 1.0;
        // Use the first unit in the conversion map as canonical
        var canonical = inner.keySet().iterator().next();
        Double factor = inner.get(canonical);
        return factor != null ? factor : 1.0;
    }

    private static boolean listEquals(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        // Format number with appropriate precision
        if (isInt()) {
            sb.append(asInt());
        } else {
            // Use Sass precision
            String formatted = String.format(Locale.ROOT, "%." + PRECISION + "f", value);
            // Remove trailing zeros
            formatted = formatted.replaceAll("0+$", "");
            if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
            sb.append(formatted);
        }
        for (String unit : getNumeratorUnits()) {
            sb.append(unit);
        }
        for (String unit : getDenominatorUnits()) {
            sb.append("/").append(unit);
        }
        return sb.toString();
    }
}
