package com.sass.value;

import java.util.List;

/**
 * A SassScript number with complex (multi-unit) units.
 */
public final class ComplexNumber extends SassNumber {
    private final List<String> numeratorUnits;
    private final List<String> denominatorUnits;

    public ComplexNumber(double value, List<String> numeratorUnits, List<String> denominatorUnits) {
        super(value);
        this.numeratorUnits = List.copyOf(numeratorUnits);
        this.denominatorUnits = List.copyOf(denominatorUnits);
    }

    @Override
    public List<String> getNumeratorUnits() { return numeratorUnits; }

    @Override
    public List<String> getDenominatorUnits() { return denominatorUnits; }

    @Override
    public boolean hasUnits() { return true; }

    @Override
    public boolean hasComplexUnits() {
        return numeratorUnits.size() > 1 || !denominatorUnits.isEmpty();
    }

    @Override
    public SassNumber withValue(double value) {
        return new ComplexNumber(value, numeratorUnits, denominatorUnits);
    }
}
