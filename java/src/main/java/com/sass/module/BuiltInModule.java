package com.sass.module;

import com.sass.ast.css.modifiable.ModifiableCssStylesheet;
import com.sass.callable.BuiltInCallable;
import com.sass.callable.Callable;
import com.sass.exception.SassScriptException;
import com.sass.util.FileSpan;
import com.sass.util.FuzzyMath;
import com.sass.util.SourceFile;
import com.sass.value.*;

import java.net.URI;
import java.util.*;
import java.util.function.Function;

/**
 * Factory for built-in {@code sass:*} modules.
 *
 * <p>Each {@code @use "sass:xxx"} resolves to a pre-built module exposing
 * the standard Sass built-in functions for that module.</p>
 *
 * <p>Currently implements the subset of built-in functions needed for
 * real-world SCSS compilation. Functions are added incrementally.</p>
 */
public final class BuiltInModule {

    /** Canonical URIs for each built-in module. */
    private static final Map<String, Module> CACHE = new HashMap<>();

    private BuiltInModule() {}

    /**
     * Returns the built-in module for the given name (e.g. "math", "color"),
     * or null if no such built-in module exists.
     */
    public static Module get(String name) {
        return CACHE.computeIfAbsent(name, BuiltInModule::create);
    }

    /**
     * Returns true if the URL is a built-in module URL (starts with "sass:").
     */
    public static boolean isBuiltIn(String url) {
        return url.startsWith("sass:");
    }

    /**
     * Extracts the module name from a built-in URL (e.g. "sass:math" -> "math").
     */
    public static String extractName(String url) {
        return url.substring(5);
    }

    private static Module create(String name) {
        var functions = new HashMap<String, Callable>();
        var variables = new HashMap<String, Value>();

        switch (name) {
            case "math" -> registerMathFunctions(functions, variables);
            case "color" -> registerColorFunctions(functions);
            case "string" -> registerStringFunctions(functions);
            case "map" -> registerMapFunctions(functions);
            case "meta" -> registerMetaFunctions(functions);
            case "list" -> registerListFunctions(functions);
            case "selector" -> {} // Placeholder — functions added as needed
            default -> {
                return null; // Unknown built-in module
            }
        }

        URI uri;
        try {
            uri = new URI("sass:" + name);
        } catch (Exception e) {
            uri = null;
        }

        var dummySource = new SourceFile("// built-in sass:" + name);
        var dummySpan = dummySource.span(0, 0);
        var emptyCss = new ModifiableCssStylesheet(dummySpan);

        return new Module(uri, variables, functions, Map.of(), emptyCss);
    }

    // -----------------------------------------------------------------------
    // Helper to register a function
    // -----------------------------------------------------------------------

    private static void addFn(Map<String, Callable> map, String name, String params,
                              Function<List<Value>, Value> callback) {
        map.put(name, BuiltInCallable.function(name, params, callback));
    }

    // -----------------------------------------------------------------------
    // sass:math
    // -----------------------------------------------------------------------

    private static void registerMathFunctions(Map<String, Callable> fns,
                                               Map<String, Value> vars) {
        // Variables
        vars.put("pi", SassNumber.unitless(Math.PI));
        vars.put("e", SassNumber.unitless(Math.E));
        vars.put("epsilon", SassNumber.unitless(Double.MIN_VALUE));
        vars.put("max-safe-integer", SassNumber.unitless(9007199254740991.0));
        vars.put("min-safe-integer", SassNumber.unitless(-9007199254740991.0));
        vars.put("max-number", SassNumber.unitless(Double.MAX_VALUE));
        vars.put("min-number", SassNumber.unitless(Double.MIN_NORMAL));
        vars.put("infinity", SassNumber.unitless(Double.POSITIVE_INFINITY));
        vars.put("-infinity", SassNumber.unitless(Double.NEGATIVE_INFINITY));
        vars.put("nan", SassNumber.unitless(Double.NaN));

        // ceil($number)
        addFn(fns, "ceil", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.ceil(n.getValue()));
        });

        // floor($number)
        addFn(fns, "floor", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.floor(n.getValue()));
        });

        // round($number)
        addFn(fns, "round", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(fuzzyRound(n.getValue()));
        });

        // abs($number)
        addFn(fns, "abs", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.abs(n.getValue()));
        });

        // sqrt($number)
        addFn(fns, "sqrt", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.unitless(Math.sqrt(n.getValue()));
        });

        // pow($base, $exponent)
        addFn(fns, "pow", "$base, $exponent", args -> {
            var base = args.get(0).assertNumber("base");
            var exponent = args.get(1).assertNumber("exponent");
            base.assertNoUnits("base");
            exponent.assertNoUnits("exponent");
            return SassNumber.unitless(Math.pow(base.getValue(), exponent.getValue()));
        });

        // log($number, $base: null)
        addFn(fns, "log", "$number, $base: null", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            if (args.size() > 1 && !(args.get(1) instanceof SassNull)) {
                var base = args.get(1).assertNumber("base");
                base.assertNoUnits("base");
                return SassNumber.unitless(
                        Math.log(n.getValue()) / Math.log(base.getValue()));
            }
            return SassNumber.unitless(Math.log(n.getValue()));
        });

        // min($numbers...) — overrides global
        addFn(fns, "min", "$numbers...", args -> {
            SassNumber min = null;
            for (var arg : args) {
                var n = arg.assertNumber(null);
                if (min == null || n.getValue() < min.getValue()) {
                    min = n;
                }
            }
            if (min == null) throw new SassScriptException("At least one argument is required.", null);
            return min;
        });

        // max($numbers...) — overrides global
        addFn(fns, "max", "$numbers...", args -> {
            SassNumber max = null;
            for (var arg : args) {
                var n = arg.assertNumber(null);
                if (max == null || n.getValue() > max.getValue()) {
                    max = n;
                }
            }
            if (max == null) throw new SassScriptException("At least one argument is required.", null);
            return max;
        });

        // clamp($min, $number, $max)
        addFn(fns, "clamp", "$min, $number, $max", args -> {
            var min = args.get(0).assertNumber("min");
            var n = args.get(1).assertNumber("number");
            var max = args.get(2).assertNumber("max");
            if (min.getValue() > max.getValue()) {
                throw new SassScriptException("$min must be less than or equal to $max.", null);
            }
            if (n.getValue() < min.getValue()) return min;
            if (n.getValue() > max.getValue()) return max;
            return n;
        });

        // percentage($number)
        addFn(fns, "percentage", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.withUnits(n.getValue() * 100, List.of("%"), List.of());
        });

        // random($limit: null)
        addFn(fns, "random", "$limit: null", args -> {
            if (args.isEmpty() || args.get(0) instanceof SassNull) {
                return SassNumber.unitless(Math.random());
            }
            var limit = args.get(0).assertNumber("limit").assertInt("limit");
            if (limit < 1) throw new SassScriptException("$limit: Must be greater than 0, was " + limit + ".", null);
            return SassNumber.unitless(new Random().nextInt(limit) + 1);
        });

        // div($number1, $number2)
        addFn(fns, "div", "$number1, $number2", args -> {
            var n1 = args.get(0);
            var n2 = args.get(1);
            if (n1 instanceof SassNumber num1 && n2 instanceof SassNumber num2) {
                return num1.dividedBy(num2);
            }
            throw new SassScriptException(
                    n1 + " and " + n2 + " are not compatible for math.div().", null);
        });

        // Trig functions
        addFn(fns, "sin", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return SassNumber.unitless(Math.sin(toRadians(n)));
        });
        addFn(fns, "cos", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return SassNumber.unitless(Math.cos(toRadians(n)));
        });
        addFn(fns, "tan", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return SassNumber.unitless(Math.tan(toRadians(n)));
        });
        addFn(fns, "asin", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.withUnits(Math.toDegrees(Math.asin(n.getValue())), List.of("deg"), List.of());
        });
        addFn(fns, "acos", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.withUnits(Math.toDegrees(Math.acos(n.getValue())), List.of("deg"), List.of());
        });
        addFn(fns, "atan", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.withUnits(Math.toDegrees(Math.atan(n.getValue())), List.of("deg"), List.of());
        });
        addFn(fns, "atan2", "$y, $x", args -> {
            var y = args.get(0).assertNumber("y");
            var x = args.get(1).assertNumber("x");
            return SassNumber.withUnits(Math.toDegrees(Math.atan2(y.getValue(), x.getValue())),
                    List.of("deg"), List.of());
        });

        // hypot($numbers...)
        addFn(fns, "hypot", "$numbers...", args -> {
            double sum = 0;
            for (var arg : args) {
                var n = arg.assertNumber(null);
                sum += n.getValue() * n.getValue();
            }
            return SassNumber.unitless(Math.sqrt(sum));
        });

        // is-unitless($number)
        addFn(fns, "is-unitless", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return SassBoolean.of(!n.hasUnits());
        });

        // compatible($number1, $number2)
        addFn(fns, "compatible", "$number1, $number2", args -> {
            var n1 = args.get(0).assertNumber("number1");
            var n2 = args.get(1).assertNumber("number2");
            return SassBoolean.of(n1.isComparableTo(n2));
        });

        // unit($number)
        addFn(fns, "unit", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            var sb = new StringBuilder();
            if (!n.getNumeratorUnits().isEmpty()) {
                sb.append(String.join("*", n.getNumeratorUnits()));
            }
            if (!n.getDenominatorUnits().isEmpty()) {
                if (!sb.isEmpty()) sb.append("/");
                sb.append(String.join("*", n.getDenominatorUnits()));
            }
            return new SassString(sb.toString(), true);
        });
    }

    private static double toRadians(SassNumber n) {
        if (!n.hasUnits()) return n.getValue();
        var unit = n.getNumeratorUnits().isEmpty() ? "" : n.getNumeratorUnits().get(0);
        return switch (unit) {
            case "deg" -> Math.toRadians(n.getValue());
            case "rad" -> n.getValue();
            case "grad" -> n.getValue() * Math.PI / 200;
            case "turn" -> n.getValue() * 2 * Math.PI;
            default -> n.getValue();
        };
    }

    private static double fuzzyRound(double value) {
        if (value > 0) {
            if (FuzzyMath.fuzzyLessThanOrEquals(value % 1, 0.5)) {
                return Math.floor(value);
            } else {
                return Math.ceil(value);
            }
        } else {
            if (FuzzyMath.fuzzyLessThanOrEquals(value % 1, -0.5)) {
                return Math.floor(value);
            } else {
                return Math.ceil(value);
            }
        }
    }

    // -----------------------------------------------------------------------
    // sass:color
    // -----------------------------------------------------------------------

    private static void registerColorFunctions(Map<String, Callable> fns) {
        // red($color)
        addFn(fns, "red", "$color", args -> {
            return SassNumber.unitless(args.get(0).assertColor("color").getRed());
        });
        // green($color)
        addFn(fns, "green", "$color", args -> {
            return SassNumber.unitless(args.get(0).assertColor("color").getGreen());
        });
        // blue($color)
        addFn(fns, "blue", "$color", args -> {
            return SassNumber.unitless(args.get(0).assertColor("color").getBlue());
        });
        // alpha/opacity($color)
        addFn(fns, "alpha", "$color", args -> {
            return SassNumber.unitless(args.get(0).assertColor("color").getAlpha());
        });
        addFn(fns, "opacity", "$color", args -> {
            return SassNumber.unitless(args.get(0).assertColor("color").getAlpha());
        });

        // hue($color)
        addFn(fns, "hue", "$color", args -> {
            return SassNumber.withUnits(args.get(0).assertColor("color").getHue(),
                    List.of("deg"), List.of());
        });
        // saturation($color)
        addFn(fns, "saturation", "$color", args -> {
            return SassNumber.withUnits(args.get(0).assertColor("color").getSaturation(),
                    List.of("%"), List.of());
        });
        // lightness($color)
        addFn(fns, "lightness", "$color", args -> {
            return SassNumber.withUnits(args.get(0).assertColor("color").getLightness(),
                    List.of("%"), List.of());
        });

        // mix($color1, $color2, $weight: 50%)
        addFn(fns, "mix", "$color1, $color2, $weight: 50%", args -> {
            var c1 = args.get(0).assertColor("color1");
            var c2 = args.get(1).assertColor("color2");
            double weight = 50;
            if (args.size() > 2 && !(args.get(2) instanceof SassNull)) {
                weight = args.get(2).assertNumber("weight").getValue();
            }
            return mixColors(c1, c2, weight);
        });

        // adjust($color, kwargs...)
        // For now, support common adjustments via keyword args
        addFn(fns, "adjust", "$color, $red: null, $green: null, $blue: null, $hue: null, $saturation: null, $lightness: null, $alpha: null", args -> {
            var color = args.get(0).assertColor("color");
            var dRed = numOrNull(args.get(1));
            var dGreen = numOrNull(args.get(2));
            var dBlue = numOrNull(args.get(3));
            var dHue = numOrNull(args.get(4));
            var dSat = numOrNull(args.get(5));
            var dLight = numOrNull(args.get(6));
            var dAlpha = numOrNull(args.get(7));

            if (dRed != null || dGreen != null || dBlue != null) {
                return SassColor.rgb(
                        color.getRed() + (dRed != null ? dRed : 0),
                        color.getGreen() + (dGreen != null ? dGreen : 0),
                        color.getBlue() + (dBlue != null ? dBlue : 0),
                        color.getAlpha() + (dAlpha != null ? dAlpha : 0));
            }
            if (dHue != null || dSat != null || dLight != null) {
                return SassColor.hsl(
                        color.getHue() + (dHue != null ? dHue : 0),
                        color.getSaturation() + (dSat != null ? dSat : 0),
                        color.getLightness() + (dLight != null ? dLight : 0),
                        color.getAlpha() + (dAlpha != null ? dAlpha : 0));
            }
            if (dAlpha != null) {
                return color.changeAlpha(color.getAlpha() + dAlpha);
            }
            return color;
        });

        // scale($color, kwargs...)
        addFn(fns, "scale", "$color, $red: null, $green: null, $blue: null, $saturation: null, $lightness: null, $alpha: null", args -> {
            var color = args.get(0).assertColor("color");
            var sRed = pctOrNull(args.get(1));
            var sGreen = pctOrNull(args.get(2));
            var sBlue = pctOrNull(args.get(3));
            var sSat = pctOrNull(args.get(4));
            var sLight = pctOrNull(args.get(5));
            var sAlpha = pctOrNull(args.get(6));

            if (sRed != null || sGreen != null || sBlue != null) {
                return SassColor.rgb(
                        scaleValue(color.getRed(), sRed, 255),
                        scaleValue(color.getGreen(), sGreen, 255),
                        scaleValue(color.getBlue(), sBlue, 255),
                        scaleValue(color.getAlpha(), sAlpha, 1));
            }
            if (sSat != null || sLight != null) {
                return SassColor.hsl(
                        color.getHue(),
                        scaleValue(color.getSaturation(), sSat, 100),
                        scaleValue(color.getLightness(), sLight, 100),
                        scaleValue(color.getAlpha(), sAlpha, 1));
            }
            if (sAlpha != null) {
                return color.changeAlpha(scaleValue(color.getAlpha(), sAlpha, 1));
            }
            return color;
        });

        // change($color, kwargs...)
        addFn(fns, "change", "$color, $red: null, $green: null, $blue: null, $hue: null, $saturation: null, $lightness: null, $alpha: null", args -> {
            var color = args.get(0).assertColor("color");
            var cRed = numOrNull(args.get(1));
            var cGreen = numOrNull(args.get(2));
            var cBlue = numOrNull(args.get(3));
            var cHue = numOrNull(args.get(4));
            var cSat = numOrNull(args.get(5));
            var cLight = numOrNull(args.get(6));
            var cAlpha = numOrNull(args.get(7));

            if (cRed != null || cGreen != null || cBlue != null) {
                return color.changeRgb(cRed, cGreen, cBlue, cAlpha);
            }
            if (cHue != null || cSat != null || cLight != null) {
                return color.changeHsl(cHue, cSat, cLight, cAlpha);
            }
            if (cAlpha != null) {
                return color.changeAlpha(cAlpha);
            }
            return color;
        });

        // complement($color)
        addFn(fns, "complement", "$color", args -> {
            var c = args.get(0).assertColor("color");
            return SassColor.hsl(c.getHue() + 180, c.getSaturation(), c.getLightness(), c.getAlpha());
        });

        // invert($color, $weight: 100%)
        addFn(fns, "invert", "$color, $weight: 100%", args -> {
            var c = args.get(0).assertColor("color");
            double weight = 100;
            if (args.size() > 1 && !(args.get(1) instanceof SassNull)) {
                weight = args.get(1).assertNumber("weight").getValue();
            }
            var inverted = SassColor.rgb(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            return mixColors(inverted, c, weight);
        });

        // grayscale($color)
        addFn(fns, "grayscale", "$color", args -> {
            var c = args.get(0).assertColor("color");
            return SassColor.hsl(c.getHue(), 0, c.getLightness(), c.getAlpha());
        });

        // ie-hex-str($color)
        addFn(fns, "ie-hex-str", "$color", args -> {
            var c = args.get(0).assertColor("color");
            int a = (int) Math.round(c.getAlpha() * 255);
            int r = (int) Math.round(c.getRed());
            int g = (int) Math.round(c.getGreen());
            int b = (int) Math.round(c.getBlue());
            return new SassString(String.format("#%02X%02X%02X%02X", a, r, g, b), false);
        });

        // channel($color, $channel, $space: null) — CSS Color Level 4
        addFn(fns, "channel", "$color, $channel, $space: null", args -> {
            var color = args.get(0).assertColor("color");
            var channel = args.get(1).assertString("channel").getText().toLowerCase();
            // $space is ignored for now — we only support rgb channels
            return switch (channel) {
                case "red" -> SassNumber.unitless(color.getRed());
                case "green" -> SassNumber.unitless(color.getGreen());
                case "blue" -> SassNumber.unitless(color.getBlue());
                case "alpha" -> SassNumber.unitless(color.getAlpha());
                case "hue" -> SassNumber.withUnits(color.getHue(), List.of("deg"), List.of());
                case "saturation" -> SassNumber.withUnits(color.getSaturation(), List.of("%"), List.of());
                case "lightness" -> SassNumber.withUnits(color.getLightness(), List.of("%"), List.of());
                default -> throw new SassScriptException(
                        "Unknown channel \"" + channel + "\".", "channel");
            };
        });

        // darken($color, $amount)
        addFn(fns, "darken", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation(),
                    c.getLightness() - amount, c.getAlpha());
        });

        // lighten($color, $amount)
        addFn(fns, "lighten", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation(),
                    c.getLightness() + amount, c.getAlpha());
        });

        // saturate($color, $amount)
        addFn(fns, "saturate", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation() + amount,
                    c.getLightness(), c.getAlpha());
        });

        // desaturate($color, $amount)
        addFn(fns, "desaturate", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation() - amount,
                    c.getLightness(), c.getAlpha());
        });

        // adjust-hue($color, $degrees)
        addFn(fns, "adjust-hue", "$color, $degrees", args -> {
            var c = args.get(0).assertColor("color");
            var degrees = args.get(1).assertNumber("degrees").getValue();
            return SassColor.hsl(c.getHue() + degrees, c.getSaturation(),
                    c.getLightness(), c.getAlpha());
        });

        // opacify/fade-in($color, $amount)
        addFn(fns, "opacify", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return c.changeAlpha(c.getAlpha() + amount);
        });
        addFn(fns, "fade-in", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return c.changeAlpha(c.getAlpha() + amount);
        });

        // transparentize/fade-out($color, $amount)
        addFn(fns, "transparentize", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return c.changeAlpha(c.getAlpha() - amount);
        });
        addFn(fns, "fade-out", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return c.changeAlpha(c.getAlpha() - amount);
        });

        // hwb($hue, $whiteness, $blackness, $alpha: 1)
        addFn(fns, "hwb", "$hue, $whiteness, $blackness, $alpha: 1", args -> {
            double h = args.get(0).assertNumber("hue").getValue();
            double w = args.get(1).assertNumber("whiteness").getValue() / 100;
            double b = args.get(2).assertNumber("blackness").getValue() / 100;
            double a = 1;
            if (args.size() > 3 && !(args.get(3) instanceof SassNull)) {
                a = args.get(3).assertNumber("alpha").getValue();
            }
            // HWB to RGB
            double ratio = w + b;
            if (ratio > 1) { w /= ratio; b /= ratio; }
            double f = 1 - w - b;
            double hNorm = ((h % 360) + 360) % 360;
            // Convert via HSL/direct
            double r = hwbChannel(hNorm, w, f, 0);
            double g = hwbChannel(hNorm, w, f, 120);
            double bl = hwbChannel(hNorm, w, f, 240);
            return SassColor.rgb(r * 255, g * 255, bl * 255, a);
        });

        // whiteness($color) and blackness($color) — CSS Color Level 4
        addFn(fns, "whiteness", "$color", args -> {
            var c = args.get(0).assertColor("color");
            double minRgb = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue())) / 255;
            return SassNumber.withUnits(minRgb * 100, List.of("%"), List.of());
        });
        addFn(fns, "blackness", "$color", args -> {
            var c = args.get(0).assertColor("color");
            double maxRgb = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue())) / 255;
            return SassNumber.withUnits((1 - maxRgb) * 100, List.of("%"), List.of());
        });
    }

    /** HWB helper: compute a single RGB channel. */
    private static double hwbChannel(double hue, double white, double f, double offset) {
        double h = ((hue - offset) % 360 + 360) % 360;
        double val;
        if (h < 60) val = white + f * h / 60;
        else if (h < 180) val = white + f;
        else if (h < 240) val = white + f * (240 - h) / 60;
        else val = white;
        return FuzzyMath.fuzzyClamp(val, 0, 1);
    }

    /** Mix two colors with a given weight (0-100). */
    private static SassColor mixColors(SassColor c1, SassColor c2, double weight) {
        double normalizedWeight = weight / 100;
        double alphaDistance = c1.getAlpha() - c2.getAlpha();
        double combinedWeight = normalizedWeight * 2 - 1;
        double w1;
        if (combinedWeight * alphaDistance == -1) {
            w1 = combinedWeight;
        } else {
            w1 = (combinedWeight + alphaDistance) / (1 + combinedWeight * alphaDistance);
        }
        w1 = (w1 + 1) / 2;
        double w2 = 1 - w1;
        return SassColor.rgb(
                c1.getRed() * w1 + c2.getRed() * w2,
                c1.getGreen() * w1 + c2.getGreen() * w2,
                c1.getBlue() * w1 + c2.getBlue() * w2,
                c1.getAlpha() * normalizedWeight + c2.getAlpha() * (1 - normalizedWeight));
    }

    private static Double numOrNull(Value v) {
        if (v instanceof SassNull || v == null) return null;
        return v.assertNumber(null).getValue();
    }

    private static Double pctOrNull(Value v) {
        if (v instanceof SassNull || v == null) return null;
        return v.assertNumber(null).getValue() / 100.0;
    }

    private static double scaleValue(double current, Double factor, double max) {
        if (factor == null) return current;
        return current + (factor > 0 ? max - current : current) * factor;
    }

    // -----------------------------------------------------------------------
    // sass:string
    // -----------------------------------------------------------------------

    private static void registerStringFunctions(Map<String, Callable> fns) {
        // quote($string)
        addFn(fns, "quote", "$string", args -> {
            var s = args.get(0).assertString("string");
            return new SassString(s.getText(), true);
        });

        // unquote($string)
        addFn(fns, "unquote", "$string", args -> {
            var s = args.get(0).assertString("string");
            return new SassString(s.getText(), false);
        });

        // str-length($string) / length($string)
        addFn(fns, "length", "$string", args -> {
            var s = args.get(0).assertString("string");
            return SassNumber.unitless(s.sassLength());
        });

        // str-index($string, $substring)
        addFn(fns, "index", "$string, $substring", args -> {
            var s = args.get(0).assertString("string");
            var sub = args.get(1).assertString("substring");
            int idx = s.getText().indexOf(sub.getText());
            if (idx < 0) return SassNull.INSTANCE;
            // Convert to Sass 1-based codepoint index
            int cpIdx = (int) s.getText().substring(0, idx).codePoints().count() + 1;
            return SassNumber.unitless(cpIdx);
        });

        // str-insert($string, $insert, $index)
        addFn(fns, "insert", "$string, $insert, $index", args -> {
            var s = args.get(0).assertString("string");
            var insert = args.get(1).assertString("insert");
            int sassIdx = args.get(2).assertNumber("index").assertInt("index");

            var text = s.getText();
            int len = s.sassLength();
            int codeUnitIdx;
            if (sassIdx > 0) {
                codeUnitIdx = codepointToCodeUnit(text, Math.min(sassIdx - 1, len));
            } else if (sassIdx == 0) {
                codeUnitIdx = 0;
            } else {
                // Negative index: -1 means end
                int cpIdx = Math.max(len + sassIdx + 1, 0);
                codeUnitIdx = codepointToCodeUnit(text, cpIdx);
            }

            var result = text.substring(0, codeUnitIdx) + insert.getText() + text.substring(codeUnitIdx);
            return new SassString(result, s.hasQuotes());
        });

        // str-slice($string, $start-at, $end-at: -1)
        addFn(fns, "slice", "$string, $start-at, $end-at: -1", args -> {
            var s = args.get(0).assertString("string");
            int startAt = args.get(1).assertNumber("start-at").assertInt("start-at");
            int endAt = -1;
            if (args.size() > 2 && !(args.get(2) instanceof SassNull)) {
                endAt = args.get(2).assertNumber("end-at").assertInt("end-at");
            }

            var text = s.getText();
            int len = s.sassLength();

            // Resolve indices
            int start = startAt > 0 ? startAt - 1 : Math.max(len + startAt, 0);
            int end = endAt > 0 ? endAt : Math.max(len + endAt + 1, 0);

            if (start >= len || start >= end) {
                return new SassString("", s.hasQuotes());
            }

            start = Math.max(start, 0);
            end = Math.min(end, len);

            int startUnit = codepointToCodeUnit(text, start);
            int endUnit = codepointToCodeUnit(text, end);
            return new SassString(text.substring(startUnit, endUnit), s.hasQuotes());
        });

        // to-upper-case($string)
        addFn(fns, "to-upper-case", "$string", args -> {
            var s = args.get(0).assertString("string");
            return new SassString(s.getText().toUpperCase(), s.hasQuotes());
        });

        // to-lower-case($string)
        addFn(fns, "to-lower-case", "$string", args -> {
            var s = args.get(0).assertString("string");
            return new SassString(s.getText().toLowerCase(), s.hasQuotes());
        });

        // unique-id()
        addFn(fns, "unique-id", "", args -> {
            return new SassString("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), false);
        });

        // split($string, $separator, $limit: -1) — new in Dart Sass 1.57
        addFn(fns, "split", "$string, $separator, $limit: -1", args -> {
            var s = args.get(0).assertString("string");
            var sep = args.get(1).assertString("separator");
            int limit = -1;
            if (args.size() > 2 && !(args.get(2) instanceof SassNull)) {
                limit = args.get(2).assertNumber("limit").assertInt("limit");
            }
            String[] parts;
            if (sep.getText().isEmpty()) {
                // Split into individual characters
                var chars = new ArrayList<Value>();
                s.getText().codePoints().forEach(cp ->
                        chars.add(new SassString(new String(Character.toChars(cp)), s.hasQuotes())));
                return new SassList(chars, ListSeparator.COMMA);
            }
            parts = limit >= 0
                    ? s.getText().split(java.util.regex.Pattern.quote(sep.getText()), limit)
                    : s.getText().split(java.util.regex.Pattern.quote(sep.getText()));
            var result = new ArrayList<Value>();
            for (var part : parts) {
                result.add(new SassString(part, s.hasQuotes()));
            }
            return new SassList(result, ListSeparator.COMMA);
        });
    }

    private static int codepointToCodeUnit(String text, int cpIndex) {
        int cuIndex = 0;
        for (int i = 0; i < cpIndex && cuIndex < text.length(); i++) {
            if (Character.isHighSurrogate(text.charAt(cuIndex))) {
                cuIndex += 2;
            } else {
                cuIndex++;
            }
        }
        return Math.min(cuIndex, text.length());
    }

    // -----------------------------------------------------------------------
    // sass:map
    // -----------------------------------------------------------------------

    private static void registerMapFunctions(Map<String, Callable> fns) {
        // map.get($map, $key, $keys...)
        addFn(fns, "get", "$map, $key, $keys...", args -> {
            var map = args.get(0).assertMap("map");
            var key = args.get(1);
            var value = map.getContents().get(key);
            // Deep get for additional keys
            for (int i = 2; i < args.size(); i++) {
                if (value == null) return SassNull.INSTANCE;
                if (value instanceof SassMap nestedMap) {
                    value = nestedMap.getContents().get(args.get(i));
                } else {
                    return SassNull.INSTANCE;
                }
            }
            return value != null ? value : SassNull.INSTANCE;
        });

        // map.set($map, $keys-and-value...)
        addFn(fns, "set", "$map, $args...", args -> {
            if (args.size() < 3) {
                throw new SassScriptException("map.set() requires at least 3 arguments.", null);
            }
            var map = args.get(0).assertMap("map");
            var newContents = new LinkedHashMap<>(map.getContents());
            var key = args.get(1);
            var value = args.get(2);
            newContents.put(key, value);
            return new SassMap(newContents);
        });

        // map.merge($map1, $map2)
        addFn(fns, "merge", "$map1, $map2", args -> {
            var map1 = args.get(0).assertMap("map1");
            var map2 = args.get(1).assertMap("map2");
            var merged = new LinkedHashMap<>(map1.getContents());
            merged.putAll(map2.getContents());
            return new SassMap(merged);
        });

        // map.remove($map, $keys...)
        addFn(fns, "remove", "$map, $keys...", args -> {
            var map = args.get(0).assertMap("map");
            var result = new LinkedHashMap<>(map.getContents());
            for (int i = 1; i < args.size(); i++) {
                result.remove(args.get(i));
            }
            return new SassMap(result);
        });

        // map.keys($map)
        addFn(fns, "keys", "$map", args -> {
            var map = args.get(0).assertMap("map");
            return new SassList(new ArrayList<>(map.getContents().keySet()), ListSeparator.COMMA);
        });

        // map.values($map)
        addFn(fns, "values", "$map", args -> {
            var map = args.get(0).assertMap("map");
            return new SassList(new ArrayList<>(map.getContents().values()), ListSeparator.COMMA);
        });

        // map.has-key($map, $key, $keys...)
        addFn(fns, "has-key", "$map, $key, $keys...", args -> {
            var map = args.get(0).assertMap("map");
            var key = args.get(1);
            if (args.size() == 2) {
                return SassBoolean.of(map.getContents().containsKey(key));
            }
            // Deep has-key
            var value = map.getContents().get(key);
            for (int i = 2; i < args.size() - 1; i++) {
                if (value == null || !(value instanceof SassMap nested)) {
                    return SassBoolean.FALSE;
                }
                value = nested.getContents().get(args.get(i));
            }
            if (value == null || !(value instanceof SassMap lastMap)) {
                return SassBoolean.FALSE;
            }
            return SassBoolean.of(lastMap.getContents().containsKey(args.get(args.size() - 1)));
        });

        // map.deep-merge($map1, $map2)
        addFn(fns, "deep-merge", "$map1, $map2", args -> {
            var map1 = args.get(0).assertMap("map1");
            var map2 = args.get(1).assertMap("map2");
            return deepMerge(map1, map2);
        });

        // map.deep-remove($map, $keys...)
        addFn(fns, "deep-remove", "$map, $keys...", args -> {
            if (args.size() < 2) {
                throw new SassScriptException("map.deep-remove() requires at least 2 arguments.", null);
            }
            var map = args.get(0).assertMap("map");
            if (args.size() == 2) {
                var result = new LinkedHashMap<>(map.getContents());
                result.remove(args.get(1));
                return new SassMap(result);
            }
            return deepRemove(map, args.subList(1, args.size()));
        });
    }

    private static SassMap deepMerge(SassMap map1, SassMap map2) {
        var result = new LinkedHashMap<>(map1.getContents());
        for (var entry : map2.getContents().entrySet()) {
            var existing = result.get(entry.getKey());
            if (existing instanceof SassMap existingMap && entry.getValue() instanceof SassMap valueMap) {
                result.put(entry.getKey(), deepMerge(existingMap, valueMap));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return new SassMap(result);
    }

    private static SassMap deepRemove(SassMap map, List<Value> keys) {
        var result = new LinkedHashMap<>(map.getContents());
        var firstKey = keys.get(0);
        if (keys.size() == 1) {
            result.remove(firstKey);
        } else {
            var nested = result.get(firstKey);
            if (nested instanceof SassMap nestedMap) {
                result.put(firstKey, deepRemove(nestedMap, keys.subList(1, keys.size())));
            }
        }
        return new SassMap(result);
    }

    // -----------------------------------------------------------------------
    // sass:meta
    // -----------------------------------------------------------------------

    private static void registerMetaFunctions(Map<String, Callable> fns) {
        // type-of($value) — also available as type_of in Sass
        addFn(fns, "type-of", "$value", args -> {
            var v = args.get(0);
            return new SassString(typeOf(v), false);
        });
        // Also register with underscore variant (Sass allows both)
        fns.put("type_of", fns.get("type-of"));

        // inspect($value)
        addFn(fns, "inspect", "$value", args -> {
            var v = args.get(0);
            if (v instanceof SassNull) return new SassString("null", false);
            return new SassString(v.toString(), false);
        });

        // keywords($args)
        addFn(fns, "keywords", "$args", args -> {
            // For now, return an empty map
            return SassMap.EMPTY;
        });

        // variable-exists($name)
        addFn(fns, "variable-exists", "$name", args -> {
            // This requires environment access — handled at evaluator level
            // When called from a module, always return false
            return SassBoolean.FALSE;
        });

        // function-exists($name, $module: null)
        addFn(fns, "function-exists", "$name, $module: null", args -> {
            return SassBoolean.FALSE;
        });

        // mixin-exists($name, $module: null)
        addFn(fns, "mixin-exists", "$name, $module: null", args -> {
            return SassBoolean.FALSE;
        });

        // global-variable-exists($name, $module: null)
        addFn(fns, "global-variable-exists", "$name, $module: null", args -> {
            return SassBoolean.FALSE;
        });

        // content-exists()
        addFn(fns, "content-exists", "", args -> {
            return SassBoolean.FALSE;
        });

        // feature-exists($feature)
        addFn(fns, "feature-exists", "$feature", args -> {
            return SassBoolean.FALSE;
        });

        // get-function($name, $css: false, $module: null)
        addFn(fns, "get-function", "$name, $css: false, $module: null", args -> {
            var name = args.get(0).assertString("name").getText();
            return new SassFunction(name);
        });

        // call($function, $args...)
        addFn(fns, "call", "$function, $args...", args -> {
            // Simplified — full implementation requires evaluator context
            throw new SassScriptException(
                    "meta.call() is not yet fully supported in this implementation.", null);
        });

        // module-variables($module)
        addFn(fns, "module-variables", "$module", args -> {
            return SassMap.EMPTY;
        });

        // module-functions($module)
        addFn(fns, "module-functions", "$module", args -> {
            return SassMap.EMPTY;
        });
    }

    private static String typeOf(Value v) {
        if (v instanceof SassNumber) return "number";
        if (v instanceof SassString) return "string";
        if (v instanceof SassColor) return "color";
        if (v instanceof SassList) return "list";
        if (v instanceof SassMap) return "map";
        if (v instanceof SassBoolean) return "bool";
        if (v instanceof SassNull) return "null";
        if (v instanceof SassFunction) return "function";
        return "unknown";
    }

    // -----------------------------------------------------------------------
    // sass:list
    // -----------------------------------------------------------------------

    private static void registerListFunctions(Map<String, Callable> fns) {
        // length($list)
        addFn(fns, "length", "$list", args -> {
            return SassNumber.unitless(args.get(0).asList().size());
        });

        // nth($list, $n)
        addFn(fns, "nth", "$list, $n", args -> {
            var list = args.get(0).asList();
            int n = args.get(1).assertNumber("n").assertInt("n");
            if (n == 0) throw new SassScriptException("List index may not be 0.", null);
            int index = n > 0 ? n - 1 : list.size() + n;
            if (index < 0 || index >= list.size()) {
                throw new SassScriptException("Invalid index " + n + " for a list with " + list.size() + " elements.", null);
            }
            return list.get(index);
        });

        // set-nth($list, $n, $value)
        addFn(fns, "set-nth", "$list, $n, $value", args -> {
            var list = new ArrayList<>(args.get(0).asList());
            int n = args.get(1).assertNumber("n").assertInt("n");
            if (n == 0) throw new SassScriptException("List index may not be 0.", null);
            int index = n > 0 ? n - 1 : list.size() + n;
            if (index < 0 || index >= list.size()) {
                throw new SassScriptException("Invalid index " + n + " for a list with " + list.size() + " elements.", null);
            }
            list.set(index, args.get(2));
            return new SassList(list, args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE);
        });

        // join($list1, $list2, $separator: auto, $bracketed: auto)
        addFn(fns, "join", "$list1, $list2, $separator: auto, $bracketed: auto", args -> {
            var result = new ArrayList<>(args.get(0).asList());
            result.addAll(args.get(1).asList());
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassList(result, sep);
        });

        // append($list, $val, $separator: auto)
        addFn(fns, "append", "$list, $val, $separator: auto", args -> {
            var result = new ArrayList<>(args.get(0).asList());
            result.add(args.get(1));
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassList(result, sep);
        });

        // zip($lists...)
        addFn(fns, "zip", "$lists...", args -> {
            if (args.isEmpty()) return SassList.EMPTY;
            var lists = args.stream().map(Value::asList).toList();
            int minLen = lists.stream().mapToInt(List::size).min().orElse(0);
            var result = new ArrayList<Value>();
            for (int i = 0; i < minLen; i++) {
                var row = new ArrayList<Value>();
                for (var list : lists) {
                    row.add(list.get(i));
                }
                result.add(new SassList(row, ListSeparator.SPACE));
            }
            return new SassList(result, ListSeparator.COMMA);
        });

        // index($list, $value)
        addFn(fns, "index", "$list, $value", args -> {
            var list = args.get(0).asList();
            var value = args.get(1);
            int idx = list.indexOf(value);
            return idx >= 0 ? SassNumber.unitless(idx + 1) : SassNull.INSTANCE;
        });

        // separator($list)
        addFn(fns, "separator", "$list", args -> {
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassString(sep == ListSeparator.COMMA ? "comma" : "space", false);
        });

        // is-bracketed($list)
        addFn(fns, "is-bracketed", "$list", args -> {
            return SassBoolean.of(args.get(0) instanceof SassList sl && sl.hasBrackets());
        });

        // slash($elements...)
        addFn(fns, "slash", "$elements...", args -> {
            if (args.size() < 2) {
                throw new SassScriptException("list.slash() requires at least 2 arguments.", null);
            }
            return new SassList(new ArrayList<>(args), ListSeparator.SLASH);
        });
    }
}
