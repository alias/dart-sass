package com.sass.value;

import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A SassScript calculation value (e.g., {@code calc()}, {@code min()}, {@code max()}, {@code clamp()}).
 * <p>
 * Calculations are represented as a name and a list of arguments that can be
 * numbers, strings, other calculations, or operations.
 */
public final class SassCalculation extends Value {
    private final String name;
    private final List<Object> arguments;

    private SassCalculation(String name, List<Object> arguments) {
        this.name = name;
        this.arguments = List.copyOf(arguments);
    }

    /** Creates a calc() calculation. */
    public static SassCalculation calc(Object argument) {
        return new SassCalculation("calc", List.of(argument));
    }

    /** Creates a min() calculation. */
    public static SassCalculation min(List<Object> arguments) {
        return new SassCalculation("min", arguments);
    }

    /** Creates a max() calculation. */
    public static SassCalculation max(List<Object> arguments) {
        return new SassCalculation("max", arguments);
    }

    /** Creates a clamp() calculation. */
    public static SassCalculation clamp(Object min, @Nullable Object value, @Nullable Object max) {
        var args = new java.util.ArrayList<>();
        args.add(min);
        if (value != null) args.add(value);
        if (max != null) args.add(max);
        return new SassCalculation("clamp", args);
    }

    /** The calculation name (e.g., "calc", "min", "max", "clamp"). */
    public String getName() { return name; }

    /** The arguments to the calculation. */
    public List<Object> getArguments() { return arguments; }

    @Override
    public SassCalculation assertCalculation(@Nullable String name) { return this; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitCalculation(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SassCalculation c &&
                name.equals(c.name) &&
                arguments.equals(c.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder(name).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Represents a binary operation within a calculation.
     */
    public record CalculationOperation(String operator, Object left, Object right) {
        @Override
        public String toString() {
            return left + " " + operator + " " + right;
        }
    }

    /**
     * Represents an interpolation within a calculation.
     */
    public record CalculationInterpolation(String value) {
        @Override
        public String toString() { return value; }
    }
}
