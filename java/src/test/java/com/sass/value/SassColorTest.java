package com.sass.value;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SassColorTest {

    @Test
    void createsRgbColor() {
        var c = SassColor.rgb(255, 0, 128);
        assertThat(c.getRed()).isEqualTo(255);
        assertThat(c.getGreen()).isEqualTo(0);
        assertThat(c.getBlue()).isEqualTo(128);
        assertThat(c.getAlpha()).isEqualTo(1.0);
    }

    @Test
    void clampsRgbValues() {
        var c = SassColor.rgb(300, -10, 128, 1.5);
        assertThat(c.getRed()).isEqualTo(255);
        assertThat(c.getGreen()).isEqualTo(0);
        assertThat(c.getBlue()).isEqualTo(128);
        assertThat(c.getAlpha()).isEqualTo(1.0);
    }

    @Test
    void createsHslColor() {
        // Pure red: hsl(0, 100%, 50%)
        var c = SassColor.hsl(0, 100, 50);
        assertThat(c.getRed()).isCloseTo(255, within(0.5));
        assertThat(c.getGreen()).isCloseTo(0, within(0.5));
        assertThat(c.getBlue()).isCloseTo(0, within(0.5));
    }

    @Test
    void convertsRgbToHsl() {
        // Pure red
        var c = SassColor.rgb(255, 0, 0);
        assertThat(c.getHue()).isCloseTo(0, within(0.01));
        assertThat(c.getSaturation()).isCloseTo(100, within(0.01));
        assertThat(c.getLightness()).isCloseTo(50, within(0.01));
    }

    @Test
    void convertsGreenToHsl() {
        var c = SassColor.rgb(0, 128, 0);
        assertThat(c.getHue()).isCloseTo(120, within(0.01));
    }

    @Test
    void changeRgbChannels() {
        var c = SassColor.rgb(255, 0, 0);
        var changed = c.changeRgb(null, 255.0, null, null);
        assertThat(changed.getRed()).isEqualTo(255);
        assertThat(changed.getGreen()).isEqualTo(255);
        assertThat(changed.getBlue()).isEqualTo(0);
    }

    @Test
    void changeAlpha() {
        var c = SassColor.rgb(255, 0, 0);
        var changed = c.changeAlpha(0.5);
        assertThat(changed.getAlpha()).isEqualTo(0.5);
        assertThat(changed.getRed()).isEqualTo(255);
    }

    @Test
    void assertColorReturnsThis() {
        var c = SassColor.rgb(0, 0, 0);
        assertThat(c.assertColor()).isSameAs(c);
    }

    @Test
    void equalityComparesRgba() {
        assertThat(SassColor.rgb(255, 0, 0)).isEqualTo(SassColor.rgb(255, 0, 0));
        assertThat(SassColor.rgb(255, 0, 0)).isNotEqualTo(SassColor.rgb(0, 255, 0));
    }

    @Test
    void hslProducesEquivalentRgb() {
        // HSL(120, 100%, 50%) = RGB(0, 255, 0)
        var hsl = SassColor.hsl(120, 100, 50);
        var rgb = SassColor.rgb(0, 255, 0);
        assertThat(hsl).isEqualTo(rgb);
    }

    @Test
    void toStringProducesHex() {
        assertThat(SassColor.rgb(255, 0, 0).toString()).isEqualTo("#ff0000");
        assertThat(SassColor.rgb(0, 128, 255).toString()).isEqualTo("#0080ff");
    }

    @Test
    void toStringWithAlpha() {
        var c = SassColor.rgb(255, 0, 0, 0.5);
        assertThat(c.toString()).contains("rgba");
        assertThat(c.toString()).contains("0.5");
    }
}
