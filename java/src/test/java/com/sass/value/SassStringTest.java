package com.sass.value;

import com.sass.exception.SassScriptException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SassStringTest {

    @Test
    void createsQuotedString() {
        var s = new SassString("hello", true);
        assertThat(s.getText()).isEqualTo("hello");
        assertThat(s.hasQuotes()).isTrue();
    }

    @Test
    void createsUnquotedString() {
        var s = new SassString("hello", false);
        assertThat(s.getText()).isEqualTo("hello");
        assertThat(s.hasQuotes()).isFalse();
    }

    @Test
    void sassLengthCountsCodePoints() {
        // "a😊b" has 3 code points but 4 UTF-16 code units
        var s = new SassString("a\uD83D\uDE0Ab", true);
        assertThat(s.sassLength()).isEqualTo(3);
    }

    @Test
    void sassLengthForAscii() {
        assertThat(new SassString("hello").sassLength()).isEqualTo(5);
    }

    @Test
    void emptyQuotedIsBlankFalse() {
        assertThat(SassString.empty(true).isBlank()).isFalse();
    }

    @Test
    void emptyUnquotedIsBlankTrue() {
        assertThat(SassString.empty(false).isBlank()).isTrue();
    }

    @Test
    void assertStringReturnsThis() {
        var s = new SassString("hello");
        assertThat(s.assertString()).isSameAs(s);
    }

    @Test
    void nonStringThrowsOnAssertString() {
        assertThatThrownBy(() -> SassBoolean.TRUE.assertString())
                .isInstanceOf(SassScriptException.class)
                .hasMessageContaining("is not a string");
    }

    @Test
    void equalityBasedOnText() {
        assertThat(new SassString("hello", true)).isEqualTo(new SassString("hello", false));
    }

    @Test
    void plusConcatenatesStrings() {
        var a = new SassString("hello", true);
        var b = new SassString(" world", false);
        var result = (SassString) a.plus(b);
        assertThat(result.getText()).isEqualTo("hello world");
        assertThat(result.hasQuotes()).isTrue(); // keeps left's quoting
    }

    @Test
    void plusConcatenatesWithNonString() {
        var a = new SassString("color: ", false);
        var b = SassNumber.create(42, "px");
        var result = (SassString) a.plus(b);
        assertThat(result.getText()).isEqualTo("color: 42px");
        assertThat(result.hasQuotes()).isFalse();
    }

    @Test
    void isSpecialNumberDetectsCalc() {
        assertThat(new SassString("calc(1 + 2)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("var(--x)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("min(1, 2)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("max(1, 2)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("clamp(1, 2, 3)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("env(safe-area)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("attr(data-x)", false).isSpecialNumber()).isTrue();
        assertThat(new SassString("hello", false).isSpecialNumber()).isFalse();
    }

    @Test
    void quotedStringIsNotSpecialNumber() {
        assertThat(new SassString("calc(1 + 2)", true).isSpecialNumber()).isFalse();
    }

    @Test
    void sassIndexToRuneIndexPositive() {
        var s = new SassString("abcde", true);
        assertThat(s.sassIndexToRuneIndex(SassNumber.create(1), null)).isEqualTo(0);
        assertThat(s.sassIndexToRuneIndex(SassNumber.create(3), null)).isEqualTo(2);
        assertThat(s.sassIndexToRuneIndex(SassNumber.create(5), null)).isEqualTo(4);
    }

    @Test
    void sassIndexToRuneIndexNegative() {
        var s = new SassString("abcde", true);
        assertThat(s.sassIndexToRuneIndex(SassNumber.create(-1), null)).isEqualTo(4);
        assertThat(s.sassIndexToRuneIndex(SassNumber.create(-5), null)).isEqualTo(0);
    }

    @Test
    void sassIndexZeroThrows() {
        var s = new SassString("abc", true);
        assertThatThrownBy(() -> s.sassIndexToRuneIndex(SassNumber.create(0), null))
                .isInstanceOf(SassScriptException.class)
                .hasMessageContaining("may not be 0");
    }

    @Test
    void sassIndexOutOfRangeThrows() {
        var s = new SassString("abc", true);
        assertThatThrownBy(() -> s.sassIndexToRuneIndex(SassNumber.create(4), null))
                .isInstanceOf(SassScriptException.class)
                .hasMessageContaining("Invalid index");
    }
}
