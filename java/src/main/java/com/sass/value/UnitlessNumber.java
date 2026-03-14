package com.sass.value;

import java.util.List;

/**
 * A SassScript number with no units.
 */
public final class UnitlessNumber extends SassNumber {

    public UnitlessNumber(double value) {
        super(value);
    }

    @Override
    public List<String> getNumeratorUnits() { return List.of(); }

    @Override
    public List<String> getDenominatorUnits() { return List.of(); }

    @Override
    public boolean hasUnits() { return false; }

    @Override
    public boolean hasComplexUnits() { return false; }

    @Override
    public SassNumber withValue(double value) {
        return new UnitlessNumber(value);
    }
}
