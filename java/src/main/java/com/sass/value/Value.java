package com.sass.value;

import com.sass.exception.SassScriptException;
import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A SassScript value.
 * <p>
 * All SassScript values are unmodifiable. New values can be constructed using
 * subclass constructors. Untyped values can be cast to particular types using
 * {@code assert*()} methods, which throw user-friendly error messages if they fail.
 */
public abstract class Value {

    protected Value() {}

    /** Whether the value counts as {@code true} in an {@code @if} statement. */
    public boolean isTruthy() { return true; }

    /** The separator for this value as a list. */
    public ListSeparator getSeparator() { return ListSeparator.UNDECIDED; }

    /** Whether this value as a list has brackets. */
    public boolean hasBrackets() { return false; }

    /** This value as a list. */
    public List<Value> asList() { return List.of(this); }

    /** The length of {@link #asList()}. */
    protected int lengthAsList() { return 1; }

    /** Whether the value will be represented in CSS as the empty string. */
    public boolean isBlank() { return false; }

    /** Whether this is a value that CSS may treat as a number, such as calc() or var(). */
    public boolean isSpecialNumber() { return false; }

    /** Whether this is a call to var(). */
    public boolean isSpecialVariable() { return false; }

    /**
     * Returns null if this is {@link SassNull#INSTANCE}, returns {@code this} otherwise.
     */
    public @Nullable Value realNull() { return this; }

    /** Calls the appropriate visit method on {@code visitor}. */
    public abstract <T> T accept(ValueVisitor<T> visitor);

    /**
     * Converts a Sass index to a Java index into {@link #asList()}.
     * Sass indexes are 1-based and may be negative.
     */
    public int sassIndexToListIndex(Value sassIndex, @Nullable String name) {
        var indexValue = sassIndex.assertNumber(name);
        var index = indexValue.assertInt(name);
        if (index == 0) throw new SassScriptException("List index may not be 0.", name);
        if (Math.abs(index) > lengthAsList()) {
            throw new SassScriptException(
                    "Invalid index " + sassIndex + " for a list with " + lengthAsList() + " elements.",
                    name);
        }
        return index < 0 ? lengthAsList() + index : index - 1;
    }

    /** Throws a SassScriptException if this isn't a boolean. */
    public SassBoolean assertBoolean(@Nullable String name) {
        throw new SassScriptException(this + " is not a boolean.", name);
    }

    /** Throws a SassScriptException if this isn't a calculation. */
    public SassCalculation assertCalculation(@Nullable String name) {
        throw new SassScriptException(this + " is not a calculation.", name);
    }

    /** Throws a SassScriptException if this isn't a color. */
    public SassColor assertColor(@Nullable String name) {
        throw new SassScriptException(this + " is not a color.", name);
    }

    /** Throws a SassScriptException if this isn't a function reference. */
    public SassFunction assertFunction(@Nullable String name) {
        throw new SassScriptException(this + " is not a function reference.", name);
    }

    /** Throws a SassScriptException if this isn't a mixin reference. */
    public SassMixin assertMixin(@Nullable String name) {
        throw new SassScriptException(this + " is not a mixin reference.", name);
    }

    /** Throws a SassScriptException if this isn't a map. */
    public SassMap assertMap(@Nullable String name) {
        throw new SassScriptException(this + " is not a map.", name);
    }

    /** Returns this as a SassMap if it is one, otherwise null. */
    public @Nullable SassMap tryMap() { return null; }

    /** Throws a SassScriptException if this isn't a number. */
    public SassNumber assertNumber(@Nullable String name) {
        throw new SassScriptException(this + " is not a number.", name);
    }

    /** Throws a SassScriptException if this isn't a string. */
    public SassString assertString(@Nullable String name) {
        throw new SassScriptException(this + " is not a string.", name);
    }

    // Overloads without name parameter
    public SassBoolean assertBoolean() { return assertBoolean(null); }
    public SassCalculation assertCalculation() { return assertCalculation(null); }
    public SassColor assertColor() { return assertColor(null); }
    public SassFunction assertFunction() { return assertFunction(null); }
    public SassMixin assertMixin() { return assertMixin(null); }
    public SassMap assertMap() { return assertMap(null); }
    public SassNumber assertNumber() { return assertNumber(null); }
    public SassString assertString() { return assertString(null); }

    /** Returns a new list containing {@code contents} using this value's separator and brackets. */
    public SassList withListContents(List<Value> contents, @Nullable ListSeparator separator, @Nullable Boolean brackets) {
        return new SassList(
                contents,
                separator != null ? separator : getSeparator(),
                brackets != null ? brackets : hasBrackets());
    }

    public SassList withListContents(List<Value> contents) {
        return withListContents(contents, null, null);
    }

    // -- Arithmetic operations --

    /** The SassScript {@code =} operation. */
    public Value singleEquals(Value other) {
        return new SassString(toCssString() + "=" + other.toCssString(), false);
    }

    /** The SassScript {@code >} operation. */
    public SassBoolean greaterThan(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " > " + other + "\".");
    }

    /** The SassScript {@code >=} operation. */
    public SassBoolean greaterThanOrEquals(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " >= " + other + "\".");
    }

    /** The SassScript {@code <} operation. */
    public SassBoolean lessThan(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " < " + other + "\".");
    }

    /** The SassScript {@code <=} operation. */
    public SassBoolean lessThanOrEquals(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " <= " + other + "\".");
    }

    /** The SassScript {@code *} operation. */
    public Value times(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " * " + other + "\".");
    }

    /** The SassScript {@code %} operation. */
    public Value modulo(Value other) {
        throw new SassScriptException("Undefined operation \"" + this + " % " + other + "\".");
    }

    /** The SassScript {@code +} operation. */
    public Value plus(Value other) {
        if (other instanceof SassString s) {
            return new SassString(toCssString() + s.getText(), s.hasQuotes());
        }
        if (other instanceof SassCalculation) {
            throw new SassScriptException("Undefined operation \"" + this + " + " + other + "\".");
        }
        return new SassString(toCssString() + other.toCssString(), false);
    }

    /** The SassScript {@code -} operation. */
    public Value minus(Value other) {
        if (other instanceof SassCalculation) {
            throw new SassScriptException("Undefined operation \"" + this + " - " + other + "\".");
        }
        return new SassString(toCssString() + "-" + other.toCssString(), false);
    }

    /** The SassScript {@code /} operation. */
    public Value dividedBy(Value other) {
        return new SassString(toCssString() + "/" + other.toCssString(), false);
    }

    /** The SassScript unary {@code +} operation. */
    public Value unaryPlus() {
        return new SassString("+" + toCssString(), false);
    }

    /** The SassScript unary {@code -} operation. */
    public Value unaryMinus() {
        return new SassString("-" + toCssString(), false);
    }

    /** The SassScript unary {@code /} operation. */
    public Value unaryDivide() {
        return new SassString("/" + toCssString(), false);
    }

    /** The SassScript unary {@code not} operation. */
    public Value unaryNot() {
        return SassBoolean.FALSE;
    }

    /** Returns a copy of this without slash representation (for numbers). */
    public Value withoutSlash() { return this; }

    /**
     * Returns a valid CSS representation of this value.
     * <p>
     * Note: Full implementation requires the serializer (Phase 4).
     * For now, returns toString() as a placeholder.
     */
    public String toCssString() {
        // TODO: delegate to serializeValue() once serializer is implemented
        return toString();
    }
}
