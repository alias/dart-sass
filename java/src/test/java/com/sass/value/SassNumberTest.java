package com.sass.value;

import com.sass.exception.SassScriptException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SassNumberTest {

    @Nested
    class UnitlessNumbers {
        @Test
        void createsUnitlessNumber() {
            var n = SassNumber.create(42);
            assertThat(n.getValue()).isEqualTo(42.0);
            assertThat(n.hasUnits()).isFalse();
            assertThat(n.getNumeratorUnits()).isEmpty();
            assertThat(n.getDenominatorUnits()).isEmpty();
        }

        @Test
        void detectsIntegers() {
            assertThat(SassNumber.create(1.0).isInt()).isTrue();
            assertThat(SassNumber.create(1.5).isInt()).isFalse();
            assertThat(SassNumber.create(1.000000000001).isInt()).isTrue(); // fuzzy
        }

        @Test
        void assertIntReturnsCorrectValue() {
            assertThat(SassNumber.create(42.0).assertInt()).isEqualTo(42);
        }

        @Test
        void assertIntThrowsForNonInteger() {
            assertThatThrownBy(() -> SassNumber.create(1.5).assertInt())
                    .isInstanceOf(SassScriptException.class)
                    .hasMessageContaining("is not an int");
        }

        @Test
        void assertNoUnitsSucceeds() {
            assertThat(SassNumber.create(1).assertNoUnits()).isNotNull();
        }
    }

    @Nested
    class SingleUnitNumbers {
        @Test
        void createsNumberWithUnit() {
            var n = SassNumber.create(10, "px");
            assertThat(n.getValue()).isEqualTo(10.0);
            assertThat(n.hasUnits()).isTrue();
            assertThat(n.getNumeratorUnits()).containsExactly("px");
            assertThat(n.getDenominatorUnits()).isEmpty();
        }

        @Test
        void hasUnitChecksCorrectly() {
            var n = SassNumber.create(10, "px");
            assertThat(n.hasUnit("px")).isTrue();
            assertThat(n.hasUnit("em")).isFalse();
        }

        @Test
        void assertNoUnitsThrowsForUnitNumber() {
            assertThatThrownBy(() -> SassNumber.create(10, "px").assertNoUnits())
                    .isInstanceOf(SassScriptException.class)
                    .hasMessageContaining("to have no units");
        }
    }

    @Nested
    class Arithmetic {
        @Test
        void additionOfCompatibleUnits() {
            var a = SassNumber.create(1, "in");
            var b = SassNumber.create(96, "px");
            var result = (SassNumber) a.plus(b);
            assertThat(result.getValue()).isCloseTo(2.0, within(1e-10));
            assertThat(result.hasUnit("in")).isTrue();
        }

        @Test
        void subtraction() {
            var a = SassNumber.create(10, "px");
            var b = SassNumber.create(3, "px");
            var result = (SassNumber) a.minus(b);
            assertThat(result.getValue()).isEqualTo(7.0);
        }

        @Test
        void multiplicationCreatesComplexUnits() {
            var a = SassNumber.create(10, "px");
            var b = SassNumber.create(2, "px");
            var result = (SassNumber) a.times(b);
            assertThat(result.getValue()).isEqualTo(20.0);
            assertThat(result.getNumeratorUnits()).containsExactly("px", "px");
        }

        @Test
        void divisionCreatesComplexUnits() {
            var a = SassNumber.create(10, "px");
            var b = SassNumber.create(2, "s");
            var result = (SassNumber) a.dividedBy(b);
            assertThat(result.getValue()).isEqualTo(5.0);
            assertThat(result.getNumeratorUnits()).containsExactly("px");
            assertThat(result.getDenominatorUnits()).containsExactly("s");
        }

        @Test
        void unaryMinus() {
            var n = SassNumber.create(5, "px");
            var result = (SassNumber) n.unaryMinus();
            assertThat(result.getValue()).isEqualTo(-5.0);
        }

        @Test
        void unaryPlusReturnsThis() {
            var n = SassNumber.create(5, "px");
            assertThat(n.unaryPlus()).isSameAs(n);
        }
    }

    @Nested
    class Comparison {
        @Test
        void greaterThan() {
            assertThat(SassNumber.create(2).greaterThan(SassNumber.create(1)))
                    .isEqualTo(SassBoolean.TRUE);
            assertThat(SassNumber.create(1).greaterThan(SassNumber.create(2)))
                    .isEqualTo(SassBoolean.FALSE);
        }

        @Test
        void lessThan() {
            assertThat(SassNumber.create(1).lessThan(SassNumber.create(2)))
                    .isEqualTo(SassBoolean.TRUE);
        }

        @Test
        void comparisonAcrossCompatibleUnits() {
            var inches = SassNumber.create(1, "in");
            var pixels = SassNumber.create(95, "px"); // 1in = 96px
            assertThat(inches.greaterThan(pixels)).isEqualTo(SassBoolean.TRUE);
        }
    }

    @Nested
    class UnitConversion {
        @Test
        void convertsInchesToPixels() {
            var n = SassNumber.create(1, "in");
            double px = n.coerceValueToUnit("px", null);
            assertThat(px).isCloseTo(96.0, within(1e-10));
        }

        @Test
        void convertsDegreesToRadians() {
            var n = SassNumber.create(180, "deg");
            double rad = n.coerceValueToUnit("rad", null);
            assertThat(rad).isCloseTo(Math.PI, within(1e-10));
        }

        @Test
        void convertsSecondsToMilliseconds() {
            var n = SassNumber.create(1, "s");
            double ms = n.coerceValueToUnit("ms", null);
            assertThat(ms).isCloseTo(1000.0, within(1e-10));
        }
    }

    @Nested
    class Equality {
        @Test
        void equalNumbersAreEqual() {
            assertThat(SassNumber.create(1)).isEqualTo(SassNumber.create(1));
        }

        @Test
        void fuzzyEqualNumbersAreEqual() {
            assertThat(SassNumber.create(1.000000000001)).isEqualTo(SassNumber.create(1));
        }

        @Test
        void differentNumbersAreNotEqual() {
            assertThat(SassNumber.create(1)).isNotEqualTo(SassNumber.create(2));
        }

        @Test
        void sameValueDifferentUnitsCanBeEqual() {
            // 1in == 96px
            assertThat(SassNumber.create(1, "in")).isEqualTo(SassNumber.create(96, "px"));
        }

        @Test
        void incompatibleUnitsAreNotEqual() {
            assertThat(SassNumber.create(1, "px")).isNotEqualTo(SassNumber.create(1, "s"));
        }
    }

    @Nested
    class StringRepresentation {
        @Test
        void unitlessIntegerToString() {
            assertThat(SassNumber.create(42).toString()).isEqualTo("42");
        }

        @Test
        void unitlessDecimalToString() {
            assertThat(SassNumber.create(1.5).toString()).isEqualTo("1.5");
        }

        @Test
        void numberWithUnitToString() {
            assertThat(SassNumber.create(10, "px").toString()).isEqualTo("10px");
        }
    }
}
