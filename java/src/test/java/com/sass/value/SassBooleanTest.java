package com.sass.value;

import com.sass.exception.SassScriptException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SassBooleanTest {

    @Test
    void trueIsTruthy() {
        assertThat(SassBoolean.TRUE.isTruthy()).isTrue();
    }

    @Test
    void falseIsNotTruthy() {
        assertThat(SassBoolean.FALSE.isTruthy()).isFalse();
    }

    @Test
    void factoryReturnsSingletons() {
        assertThat(SassBoolean.of(true)).isSameAs(SassBoolean.TRUE);
        assertThat(SassBoolean.of(false)).isSameAs(SassBoolean.FALSE);
    }

    @Test
    void assertBooleanReturnsThis() {
        assertThat(SassBoolean.TRUE.assertBoolean()).isSameAs(SassBoolean.TRUE);
        assertThat(SassBoolean.FALSE.assertBoolean()).isSameAs(SassBoolean.FALSE);
    }

    @Test
    void unaryNotNegates() {
        assertThat(SassBoolean.TRUE.unaryNot()).isEqualTo(SassBoolean.FALSE);
        assertThat(SassBoolean.FALSE.unaryNot()).isEqualTo(SassBoolean.TRUE);
    }

    @Test
    void toStringProducesCorrectRepresentation() {
        assertThat(SassBoolean.TRUE.toString()).isEqualTo("true");
        assertThat(SassBoolean.FALSE.toString()).isEqualTo("false");
    }

    @Test
    void equality() {
        assertThat(SassBoolean.TRUE).isEqualTo(SassBoolean.of(true));
        assertThat(SassBoolean.FALSE).isEqualTo(SassBoolean.of(false));
        assertThat(SassBoolean.TRUE).isNotEqualTo(SassBoolean.FALSE);
    }

    @Test
    void nonBooleanThrowsOnAssertBoolean() {
        assertThatThrownBy(() -> SassNull.INSTANCE.assertBoolean())
                .isInstanceOf(SassScriptException.class)
                .hasMessageContaining("is not a boolean");
    }
}
