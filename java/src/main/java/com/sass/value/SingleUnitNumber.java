package com.sass.value;

import java.util.List;

/**
 * A SassScript number with a single numerator unit and no denominator units.
 */
public final class SingleUnitNumber extends SassNumber {
    private final String unit;

    public SingleUnitNumber(double value, String unit) {
        super(value);
        this.unit = unit;
    }

    @Override
    public List<String> getNumeratorUnits() { return List.of(unit); }

    @Override
    public List<String> getDenominatorUnits() { return List.of(); }

    @Override
    public boolean hasUnits() { return true; }

    @Override
    public boolean hasComplexUnits() { return false; }

    @Override
    protected SassNumber withValue(double value) {
        return new SingleUnitNumber(value, unit);
    }
}
