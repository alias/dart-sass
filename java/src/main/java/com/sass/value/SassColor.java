package com.sass.value;

import com.sass.exception.SassScriptException;
import com.sass.util.FuzzyMath;
import com.sass.visitor.ValueVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A SassScript color value.
 * <p>
 * This initial implementation supports legacy RGB/HSL color spaces.
 * Full color space support (Lab, LCH, OKLab, OKLch, P3, etc.) will be
 * added incrementally.
 */
public final class SassColor extends Value {
    private final double red;
    private final double green;
    private final double blue;
    private final double alpha;

    // Cached HSL values
    private @Nullable Double hue;
    private @Nullable Double saturation;
    private @Nullable Double lightness;

    private SassColor(double red, double green, double blue, double alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /** Creates an RGB color. Channel values are 0-255, alpha is 0-1. */
    public static SassColor rgb(double red, double green, double blue, double alpha) {
        return new SassColor(
                FuzzyMath.fuzzyClamp(red, 0, 255),
                FuzzyMath.fuzzyClamp(green, 0, 255),
                FuzzyMath.fuzzyClamp(blue, 0, 255),
                FuzzyMath.fuzzyClamp(alpha, 0, 1));
    }

    /** Creates an RGB color with alpha = 1. */
    public static SassColor rgb(double red, double green, double blue) {
        return rgb(red, green, blue, 1.0);
    }

    /** Creates an HSL color. Hue is 0-360, saturation/lightness are 0-100, alpha is 0-1. */
    public static SassColor hsl(double hue, double saturation, double lightness, double alpha) {
        hue = hue % 360;
        if (hue < 0) hue += 360;
        saturation = FuzzyMath.fuzzyClamp(saturation, 0, 100);
        lightness = FuzzyMath.fuzzyClamp(lightness, 0, 100);
        alpha = FuzzyMath.fuzzyClamp(alpha, 0, 1);

        double h = hue / 360;
        double s = saturation / 100;
        double l = lightness / 100;

        double r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3);
        }

        var color = new SassColor(r * 255, g * 255, b * 255, alpha);
        color.hue = hue;
        color.saturation = saturation;
        color.lightness = lightness;
        return color;
    }

    /** Creates an HSL color with alpha = 1. */
    public static SassColor hsl(double hue, double saturation, double lightness) {
        return hsl(hue, saturation, lightness, 1.0);
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }

    // -- Channel accessors --

    /** Red channel (0-255). */
    public double getRed() { return red; }

    /** Green channel (0-255). */
    public double getGreen() { return green; }

    /** Blue channel (0-255). */
    public double getBlue() { return blue; }

    /** Alpha channel (0-1). */
    public double getAlpha() { return alpha; }

    /** Hue in degrees (0-360). */
    public double getHue() {
        if (hue == null) computeHsl();
        return hue;
    }

    /** Saturation as percentage (0-100). */
    public double getSaturation() {
        if (saturation == null) computeHsl();
        return saturation;
    }

    /** Lightness as percentage (0-100). */
    public double getLightness() {
        if (lightness == null) computeHsl();
        return lightness;
    }

    private void computeHsl() {
        double r = red / 255;
        double g = green / 255;
        double b = blue / 255;

        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;

        double h, s, l;
        l = (max + min) / 2;

        if (delta == 0) {
            h = 0;
            s = 0;
        } else {
            s = l > 0.5 ? delta / (2 - max - min) : delta / (max + min);

            if (max == r) {
                h = ((g - b) / delta + (g < b ? 6 : 0)) / 6;
            } else if (max == g) {
                h = ((b - r) / delta + 2) / 6;
            } else {
                h = ((r - g) / delta + 4) / 6;
            }
        }

        this.hue = h * 360;
        this.saturation = s * 100;
        this.lightness = l * 100;
    }

    /** Returns a new color with modified channels. Null means keep original. */
    public SassColor changeRgb(@Nullable Double red, @Nullable Double green,
                                @Nullable Double blue, @Nullable Double alpha) {
        return SassColor.rgb(
                red != null ? red : this.red,
                green != null ? green : this.green,
                blue != null ? blue : this.blue,
                alpha != null ? alpha : this.alpha);
    }

    /** Returns a new color with modified HSL channels. Null means keep original. */
    public SassColor changeHsl(@Nullable Double hue, @Nullable Double saturation,
                                @Nullable Double lightness, @Nullable Double alpha) {
        return SassColor.hsl(
                hue != null ? hue : getHue(),
                saturation != null ? saturation : getSaturation(),
                lightness != null ? lightness : getLightness(),
                alpha != null ? alpha : this.alpha);
    }

    /** Returns a new color with the given alpha. */
    public SassColor changeAlpha(double alpha) {
        return SassColor.rgb(red, green, blue, alpha);
    }

    @Override
    public SassColor assertColor(@Nullable String name) { return this; }

    @Override
    public <T> T accept(ValueVisitor<T> visitor) {
        return visitor.visitColor(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SassColor o)) return false;
        return FuzzyMath.fuzzyEquals(red, o.red) &&
                FuzzyMath.fuzzyEquals(green, o.green) &&
                FuzzyMath.fuzzyEquals(blue, o.blue) &&
                FuzzyMath.fuzzyEquals(alpha, o.alpha);
    }

    @Override
    public int hashCode() {
        return FuzzyMath.fuzzyHashCode(red) * 31 * 31 * 31 +
                FuzzyMath.fuzzyHashCode(green) * 31 * 31 +
                FuzzyMath.fuzzyHashCode(blue) * 31 +
                FuzzyMath.fuzzyHashCode(alpha);
    }

    @Override
    public String toString() {
        if (FuzzyMath.fuzzyEquals(alpha, 1.0)) {
            int r = (int) Math.round(red);
            int g = (int) Math.round(green);
            int b = (int) Math.round(blue);
            return String.format("#%02x%02x%02x", r & 0xFF, g & 0xFF, b & 0xFF);
        }
        return String.format("rgba(%s, %s, %s, %s)",
                FuzzyMath.fuzzyIsInt(red) ? String.valueOf((int) red) : String.valueOf(red),
                FuzzyMath.fuzzyIsInt(green) ? String.valueOf((int) green) : String.valueOf(green),
                FuzzyMath.fuzzyIsInt(blue) ? String.valueOf((int) blue) : String.valueOf(blue),
                alpha);
    }
}
