package com.sass.visitor;

import com.sass.ast.css.CssValue;
import com.sass.ast.css.modifiable.*;
import com.sass.ast.sass.*;
import com.sass.callable.BuiltInCallable;
import com.sass.callable.Callable;
import com.sass.callable.PlainCssCallable;
import com.sass.callable.UserDefinedCallable;
import com.sass.environment.Environment;
import com.sass.exception.SassRuntimeException;
import com.sass.exception.SassScriptException;
import com.sass.importer.FilesystemImporter;
import com.sass.importer.ImportCache;
import com.sass.module.BuiltInModule;
import com.sass.module.Module;
import com.sass.parse.ScssParser;
import com.sass.util.FileSpan;
import com.sass.util.SourceFile;
import com.sass.value.*;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.*;

/**
 * The Sass evaluator.
 *
 * <p>Visits a Sass AST (Stylesheet) and produces a modifiable CSS AST
 * (ModifiableCssStylesheet). This implements the core compilation logic:
 * variable evaluation, interpolation, control flow, mixins, functions, etc.</p>
 */
public final class EvaluateVisitor implements StatementVisitor<@Nullable Value>, ExpressionVisitor<Value> {

    private Environment environment;
    private ModifiableCssParentNode parent;
    private ModifiableCssStylesheet root;
    /**
     * The target node for nested style rule flattening. Normally this is the same
     * as {@code root} (the stylesheet), but when an at-rule (@media, @supports)
     * is "bubbled" past a style rule, this points to the at-rule node so that
     * nested style rules are flattened inside it instead of at the stylesheet root.
     */
    private ModifiableCssParentNode styleFlattenTarget;
    private @Nullable ModifiableCssStyleRule styleRule;
    private final Map<String, BuiltInCallable> builtInFunctions;

    // Import/module system fields
    private final @Nullable FilesystemImporter importer;
    private final @Nullable ImportCache importCache;
    private @Nullable URI currentSourceUri;
    /** URIs currently being evaluated — for circular import detection. */
    private final Set<URI> activeImports = new LinkedHashSet<>();
    /** Already-loaded modules by canonical URI — for @use deduplication. */
    private final Map<URI, Module> loadedModules = new HashMap<>();
    /** Modules whose CSS has already been inlined — prevents duplicate output. */
    private final Set<URI> inlinedModuleCss = new HashSet<>();

    /** Thrown internally by @return to unwind the call stack. */
    private static final class ReturnValue extends RuntimeException {
        final Value value;
        ReturnValue(Value value) {
            super(null, null, true, false);
            this.value = value;
        }
    }

    /** Creates an evaluator without file import support (string-only compilation). */
    public EvaluateVisitor() {
        this(null, null, null);
    }

    /**
     * Creates an evaluator with file import and module support.
     *
     * @param importer the filesystem importer for resolving @import/@use URLs
     * @param cache cache for resolved paths and parsed stylesheets
     * @param sourceUri the URI of the initial stylesheet (null for string input)
     */
    public EvaluateVisitor(
            @Nullable FilesystemImporter importer,
            @Nullable ImportCache cache,
            @Nullable URI sourceUri) {
        this.environment = new Environment();
        var dummySpan = createDummySpan();
        this.root = new ModifiableCssStylesheet(dummySpan);
        this.parent = root;
        this.styleFlattenTarget = root;
        this.builtInFunctions = new HashMap<>();
        this.importer = importer;
        this.importCache = cache;
        this.currentSourceUri = sourceUri;
        if (sourceUri != null) {
            activeImports.add(sourceUri);
        }
        registerBuiltInFunctions();
    }

    /** Evaluates a stylesheet and returns the resulting CSS AST. */
    public ModifiableCssStylesheet evaluate(Stylesheet stylesheet) {
        var dummySpan = stylesheet.getSpan();
        var result = new ModifiableCssStylesheet(dummySpan);
        this.root = result;
        this.parent = result;
        this.styleFlattenTarget = result;
        this.styleRule = null;

        for (var child : stylesheet.getChildren()) {
            child.accept(this);
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Built-in functions
    // -----------------------------------------------------------------------

    private void registerBuiltInFunctions() {
        // if($condition, $if-true, $if-false)
        addBuiltIn("if", "$condition, $if-true, $if-false", args -> {
            return isTruthy(args.get(0)) ? args.get(1) : args.get(2);
        });

        // type-of($value)
        addBuiltIn("type-of", "$value", args -> {
            var value = args.get(0);
            return new SassString(getTypeOf(value), false);
        });

        // inspect($value)
        addBuiltIn("inspect", "$value", args -> {
            var value = args.get(0);
            return new SassString(CssSerializer.serializeValue(value, true, true), false);
        });

        // unit($number)
        addBuiltIn("unit", "$number", args -> {
            var number = args.get(0).assertNumber(null);
            var sb = new StringBuilder();
            if (!number.getNumeratorUnits().isEmpty()) {
                sb.append(String.join("*", number.getNumeratorUnits()));
            }
            if (!number.getDenominatorUnits().isEmpty()) {
                if (!sb.isEmpty()) sb.append("/");
                sb.append(String.join("*", number.getDenominatorUnits()));
            }
            return new SassString(sb.toString(), true);
        });

        // unitless($number)
        addBuiltIn("unitless", "$number", args -> {
            var number = args.get(0).assertNumber(null);
            return SassBoolean.of(!number.hasUnits());
        });

        // variable-exists($name)
        addBuiltIn("variable-exists", "$name", args -> {
            var name = args.get(0).assertString(null).getText();
            return SassBoolean.of(environment.variableExists(name));
        });

        // global-variable-exists($name)
        addBuiltIn("global-variable-exists", "$name", args -> {
            var name = args.get(0).assertString(null).getText();
            return SassBoolean.of(environment.globalVariableExists(name));
        });

        // function-exists($name)
        addBuiltIn("function-exists", "$name", args -> {
            var name = args.get(0).assertString(null).getText();
            return SassBoolean.of(
                    environment.functionExists(name) || builtInFunctions.containsKey(name));
        });

        // mixin-exists($name)
        addBuiltIn("mixin-exists", "$name", args -> {
            var name = args.get(0).assertString(null).getText();
            return SassBoolean.of(environment.mixinExists(name));
        });

        // --- Color constructors ---

        // rgb($red, $green, $blue) / rgb($color, $alpha) / rgb($channels)
        addBuiltIn("rgb", "$red-or-channels, $green: null, $blue: null, $alpha: null", args -> {
            return evalRgba(args, false);
        });

        // rgba($red, $green, $blue, $alpha) / rgba($color, $alpha)
        addBuiltIn("rgba", "$red-or-color, $green-or-alpha: null, $blue: null, $alpha: null", args -> {
            return evalRgba(args, true);
        });

        // hsl($hue, $saturation, $lightness) / hsla(...)
        addBuiltIn("hsl", "$hue, $saturation: null, $lightness: null, $alpha: null", args -> {
            return evalHsla(args);
        });
        addBuiltIn("hsla", "$hue, $saturation: null, $lightness: null, $alpha: null", args -> {
            return evalHsla(args);
        });

        // mix($color1, $color2, $weight: 50%) — global alias
        addBuiltIn("mix", "$color1, $color2, $weight: 50%", args -> {
            var c1 = args.get(0).assertColor("color1");
            var c2 = args.get(1).assertColor("color2");
            double weight = 50;
            if (args.size() > 2 && !(args.get(2) instanceof SassNull)) {
                weight = args.get(2).assertNumber("weight").getValue();
            }
            return mixGlobalColors(c1, c2, weight);
        });

        // red($color), green($color), blue($color), alpha($color)
        addBuiltIn("red", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getRed()));
        addBuiltIn("green", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getGreen()));
        addBuiltIn("blue", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getBlue()));
        addBuiltIn("alpha", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getAlpha()));
        addBuiltIn("opacity", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getAlpha()));

        // hue($color), saturation($color), lightness($color)
        addBuiltIn("hue", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getHue(), "deg"));
        addBuiltIn("saturation", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getSaturation(), "%"));
        addBuiltIn("lightness", "$color", args -> SassNumber.create(args.get(0).assertColor("color").getLightness(), "%"));

        // adjust-hue, darken, lighten, saturate, desaturate
        addBuiltIn("adjust-hue", "$color, $degrees", args -> {
            var c = args.get(0).assertColor("color");
            var degrees = args.get(1).assertNumber("degrees").getValue();
            return SassColor.hsl(c.getHue() + degrees, c.getSaturation(), c.getLightness(), c.getAlpha());
        });
        addBuiltIn("darken", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation(), c.getLightness() - amount, c.getAlpha());
        });
        addBuiltIn("lighten", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation(), c.getLightness() + amount, c.getAlpha());
        });
        addBuiltIn("saturate", "$color, $amount", args -> {
            if (args.size() == 1 || args.get(1) instanceof SassNull) {
                // CSS filter function: saturate(amount)
                return new SassString("saturate(" + toCss(args.get(0)) + ")", false);
            }
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation() + amount, c.getLightness(), c.getAlpha());
        });
        addBuiltIn("desaturate", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            var amount = args.get(1).assertNumber("amount").getValue();
            return SassColor.hsl(c.getHue(), c.getSaturation() - amount, c.getLightness(), c.getAlpha());
        });

        // complement, invert, grayscale
        addBuiltIn("complement", "$color", args -> {
            var c = args.get(0).assertColor("color");
            return SassColor.hsl(c.getHue() + 180, c.getSaturation(), c.getLightness(), c.getAlpha());
        });
        addBuiltIn("invert", "$color, $weight: 100%", args -> {
            var c = args.get(0).assertColor("color");
            double weight = 100;
            if (args.size() > 1 && !(args.get(1) instanceof SassNull)) {
                weight = args.get(1).assertNumber("weight").getValue();
            }
            var inv = SassColor.rgb(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            return mixGlobalColors(inv, c, weight);
        });
        addBuiltIn("grayscale", "$color", args -> {
            if (args.get(0) instanceof SassNumber) {
                // CSS filter function
                return new SassString("grayscale(" + toCss(args.get(0)) + ")", false);
            }
            var c = args.get(0).assertColor("color");
            return SassColor.hsl(c.getHue(), 0, c.getLightness(), c.getAlpha());
        });

        // opacify/fade-in, transparentize/fade-out
        addBuiltIn("opacify", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            return c.changeAlpha(c.getAlpha() + args.get(1).assertNumber("amount").getValue());
        });
        addBuiltIn("fade-in", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            return c.changeAlpha(c.getAlpha() + args.get(1).assertNumber("amount").getValue());
        });
        addBuiltIn("transparentize", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            return c.changeAlpha(c.getAlpha() - args.get(1).assertNumber("amount").getValue());
        });
        addBuiltIn("fade-out", "$color, $amount", args -> {
            var c = args.get(0).assertColor("color");
            return c.changeAlpha(c.getAlpha() - args.get(1).assertNumber("amount").getValue());
        });

        // ie-hex-str($color)
        addBuiltIn("ie-hex-str", "$color", args -> {
            var c = args.get(0).assertColor("color");
            int a = (int) Math.round(c.getAlpha() * 255);
            int r = (int) Math.round(c.getRed());
            int g = (int) Math.round(c.getGreen());
            int b = (int) Math.round(c.getBlue());
            return new SassString(String.format("#%02X%02X%02X%02X", a, r, g, b), false);
        });

        // --- String functions ---
        addBuiltIn("quote", "$string", args -> new SassString(args.get(0).assertString("string").getText(), true));
        addBuiltIn("unquote", "$string", args -> new SassString(args.get(0).assertString("string").getText(), false));
        addBuiltIn("str-length", "$string", args -> SassNumber.create(args.get(0).assertString("string").sassLength()));
        addBuiltIn("str-index", "$string, $substring", args -> {
            var s = args.get(0).assertString("string");
            var sub = args.get(1).assertString("substring");
            int idx = s.getText().indexOf(sub.getText());
            return idx < 0 ? SassNull.INSTANCE : SassNumber.create(idx + 1);
        });
        addBuiltIn("str-insert", "$string, $insert, $index", args -> {
            var s = args.get(0).assertString("string");
            var insert = args.get(1).assertString("insert");
            int sassIdx = args.get(2).assertNumber("index").assertInt("index");
            var text = s.getText();
            int len = s.sassLength();
            int pos = sassIdx > 0 ? Math.min(sassIdx - 1, len) : Math.max(len + sassIdx + 1, 0);
            int codeUnitPos = 0;
            for (int i = 0; i < pos && codeUnitPos < text.length(); i++) {
                codeUnitPos += Character.isHighSurrogate(text.charAt(codeUnitPos)) ? 2 : 1;
            }
            return new SassString(text.substring(0, codeUnitPos) + insert.getText() + text.substring(codeUnitPos), s.hasQuotes());
        });
        addBuiltIn("str-slice", "$string, $start-at, $end-at: -1", args -> {
            var s = args.get(0).assertString("string");
            int startAt = args.get(1).assertNumber("start-at").assertInt("start-at");
            int endAt = -1;
            if (args.size() > 2 && !(args.get(2) instanceof SassNull)) {
                endAt = args.get(2).assertNumber("end-at").assertInt("end-at");
            }
            var text = s.getText();
            int len = s.sassLength();
            int start = startAt > 0 ? startAt - 1 : Math.max(len + startAt, 0);
            int end = endAt > 0 ? endAt : Math.max(len + endAt + 1, 0);
            if (start >= len || start >= end) return new SassString("", s.hasQuotes());
            start = Math.max(start, 0);
            end = Math.min(end, len);
            int startUnit = codepointToCodeUnit(text, start);
            int endUnit = codepointToCodeUnit(text, end);
            return new SassString(text.substring(startUnit, endUnit), s.hasQuotes());
        });
        addBuiltIn("to-upper-case", "$string", args -> new SassString(args.get(0).assertString("string").getText().toUpperCase(), args.get(0).assertString(null).hasQuotes()));
        addBuiltIn("to-lower-case", "$string", args -> new SassString(args.get(0).assertString("string").getText().toLowerCase(), args.get(0).assertString(null).hasQuotes()));
        addBuiltIn("unique-id", "", args -> new SassString("u" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8), false));

        // --- List functions ---
        addBuiltIn("length", "$list", args -> SassNumber.create(args.get(0).asList().size()));
        addBuiltIn("nth", "$list, $n", args -> {
            var list = args.get(0).asList();
            int n = args.get(1).assertNumber("n").assertInt("n");
            if (n == 0) throw new SassScriptException("List index may not be 0.", null);
            int idx = n > 0 ? n - 1 : list.size() + n;
            if (idx < 0 || idx >= list.size()) throw new SassScriptException("Invalid index " + n + ".", null);
            return list.get(idx);
        });
        addBuiltIn("join", "$list1, $list2, $separator: auto, $bracketed: auto", args -> {
            var result = new ArrayList<>(args.get(0).asList());
            result.addAll(args.get(1).asList());
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassList(result, sep);
        });
        addBuiltIn("append", "$list, $val, $separator: auto", args -> {
            var result = new ArrayList<>(args.get(0).asList());
            result.add(args.get(1));
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassList(result, sep);
        });
        addBuiltIn("zip", "$lists...", args -> {
            if (args.isEmpty()) return SassList.EMPTY;
            var lists = args.stream().map(Value::asList).toList();
            int minLen = lists.stream().mapToInt(List::size).min().orElse(0);
            var result = new ArrayList<Value>();
            for (int i = 0; i < minLen; i++) {
                var row = new ArrayList<Value>();
                for (var l : lists) row.add(l.get(i));
                result.add(new SassList(row, ListSeparator.SPACE));
            }
            return new SassList(result, ListSeparator.COMMA);
        });
        addBuiltIn("index", "$list, $value", args -> {
            int idx = args.get(0).asList().indexOf(args.get(1));
            return idx >= 0 ? SassNumber.create(idx + 1) : SassNull.INSTANCE;
        });
        addBuiltIn("list-separator", "$list", args -> {
            var sep = args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE;
            return new SassString(sep == ListSeparator.COMMA ? "comma" : "space", false);
        });
        addBuiltIn("is-bracketed", "$list", args ->
            SassBoolean.of(args.get(0) instanceof SassList sl && sl.hasBrackets()));
        addBuiltIn("set-nth", "$list, $n, $value", args -> {
            var list = new ArrayList<>(args.get(0).asList());
            int n = args.get(1).assertNumber("n").assertInt("n");
            if (n == 0) throw new SassScriptException("List index may not be 0.", null);
            int idx = n > 0 ? n - 1 : list.size() + n;
            if (idx < 0 || idx >= list.size()) throw new SassScriptException("Invalid index " + n + ".", null);
            list.set(idx, args.get(2));
            return new SassList(list, args.get(0) instanceof SassList sl ? sl.getSeparator() : ListSeparator.SPACE);
        });

        // --- Map functions ---
        addBuiltIn("map-get", "$map, $key", args -> {
            var val = args.get(0).assertMap("map").getContents().get(args.get(1));
            return val != null ? val : SassNull.INSTANCE;
        });
        addBuiltIn("map-merge", "$map1, $map2", args -> {
            var merged = new LinkedHashMap<>(args.get(0).assertMap("map1").getContents());
            merged.putAll(args.get(1).assertMap("map2").getContents());
            return new SassMap(merged);
        });
        addBuiltIn("map-remove", "$map, $keys...", args -> {
            var result = new LinkedHashMap<>(args.get(0).assertMap("map").getContents());
            for (int i = 1; i < args.size(); i++) result.remove(args.get(i));
            return new SassMap(result);
        });
        addBuiltIn("map-keys", "$map", args ->
            new SassList(new ArrayList<>(args.get(0).assertMap("map").getContents().keySet()), ListSeparator.COMMA));
        addBuiltIn("map-values", "$map", args ->
            new SassList(new ArrayList<>(args.get(0).assertMap("map").getContents().values()), ListSeparator.COMMA));
        addBuiltIn("map-has-key", "$map, $key", args ->
            SassBoolean.of(args.get(0).assertMap("map").getContents().containsKey(args.get(1))));

        // --- Math ---
        addBuiltIn("percentage", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            n.assertNoUnits("number");
            return SassNumber.withUnits(n.getValue() * 100, List.of("%"), List.of());
        });
        addBuiltIn("round", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.round(n.getValue()));
        });
        addBuiltIn("ceil", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.ceil(n.getValue()));
        });
        addBuiltIn("floor", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.floor(n.getValue()));
        });
        addBuiltIn("abs", "$number", args -> {
            var n = args.get(0).assertNumber("number");
            return n.withValue(Math.abs(n.getValue()));
        });
        addBuiltIn("min", "$numbers...", args -> {
            SassNumber min = null;
            for (var a : args) {
                var n = a.assertNumber(null);
                if (min == null || n.getValue() < min.getValue()) min = n;
            }
            if (min == null) throw new SassScriptException("At least one argument required.", null);
            return min;
        });
        addBuiltIn("max", "$numbers...", args -> {
            SassNumber max = null;
            for (var a : args) {
                var n = a.assertNumber(null);
                if (max == null || n.getValue() > max.getValue()) max = n;
            }
            if (max == null) throw new SassScriptException("At least one argument required.", null);
            return max;
        });
        addBuiltIn("random", "$limit: null", args -> {
            if (args.isEmpty() || args.get(0) instanceof SassNull) return SassNumber.create(Math.random());
            var limit = args.get(0).assertNumber("limit").assertInt("limit");
            if (limit < 1) throw new SassScriptException("$limit must be > 0.", null);
            return SassNumber.create(new java.util.Random().nextInt(limit) + 1);
        });
        addBuiltIn("comparable", "$number1, $number2", args ->
            SassBoolean.of(args.get(0).assertNumber("number1").isComparableTo(args.get(1).assertNumber("number2"))));
    }

    private void addBuiltIn(String name, String params,
                             java.util.function.Function<List<Value>, Value> callback) {
        builtInFunctions.put(name, BuiltInCallable.function(name, params, callback));
    }

    /**
     * Evaluates rgb()/rgba() arguments.
     * Handles: rgb(r,g,b), rgba(r,g,b,a), rgba(color, alpha).
     */
    private static Value evalRgba(List<Value> args, boolean allowAlpha) {
        var first = args.get(0);
        var second = args.size() > 1 ? args.get(1) : SassNull.INSTANCE;
        var third = args.size() > 2 ? args.get(2) : SassNull.INSTANCE;
        var fourth = args.size() > 3 ? args.get(3) : SassNull.INSTANCE;

        // Two-argument form: rgba($color, $alpha) or rgba(var(--foo), 0.5)
        // Matches dart-sass _rgbTwoArg(): check for special variables first.
        if (!(second instanceof SassNull) && (third instanceof SassNull)) {
            // rgba(var(--foo), 0.5) is valid CSS because --foo might be "123, 456, 789"
            if (first.isSpecialVariable()
                    || (!(first instanceof SassColor) && second.isSpecialVariable())) {
                return functionString("rgba", args);
            }
            if (first instanceof SassColor c) {
                double alpha = second.assertNumber("alpha").getValue();
                return c.changeAlpha(alpha);
            }
        }

        // Three/four-argument form: rgb($r, $g, $b) or rgba($r, $g, $b, $a)
        // Matches dart-sass _rgb(): check for special numbers first.
        if (first.isSpecialNumber() || second.isSpecialNumber()
                || third.isSpecialNumber() || fourth.isSpecialNumber()) {
            return functionString("rgba", args);
        }

        // Also check for special variables in any position
        if (first.isSpecialVariable() || second.isSpecialVariable()
                || third.isSpecialVariable() || fourth.isSpecialVariable()) {
            return functionString("rgba", args);
        }

        double r = first.assertNumber("red").getValue();
        double g = (second instanceof SassNull) ? 0 : second.assertNumber("green").getValue();
        double b = (third instanceof SassNull) ? 0 : third.assertNumber("blue").getValue();
        double a = 1;
        if (!(fourth instanceof SassNull)) {
            a = fourth.assertNumber("alpha").getValue();
        }
        return SassColor.rgb(r, g, b, a);
    }

    /** Evaluates hsl()/hsla() arguments. */
    private static Value evalHsla(List<Value> args) {
        // Matches dart-sass: check for special variables/numbers first
        for (var arg : args) {
            if (arg.isSpecialNumber() || arg.isSpecialVariable()) {
                return functionString("hsl", args);
            }
        }

        double h = args.get(0).assertNumber("hue").getValue();
        double s = (args.size() > 1 && !(args.get(1) instanceof SassNull))
                ? args.get(1).assertNumber("saturation").getValue() : 0;
        double l = (args.size() > 2 && !(args.get(2) instanceof SassNull))
                ? args.get(2).assertNumber("lightness").getValue() : 0;
        double a = 1;
        if (args.size() > 3 && !(args.get(3) instanceof SassNull)) {
            a = args.get(3).assertNumber("alpha").getValue();
        }
        return SassColor.hsl(h, s, l, a);
    }

    /**
     * Returns an unquoted SassString representing a CSS function call.
     * Matches dart-sass's {@code _functionString()}: serializes each argument
     * to CSS and joins with ", ".
     */
    private static SassString functionString(String name, List<Value> args) {
        var sb = new StringBuilder(name);
        sb.append('(');
        boolean first = true;
        for (var arg : args) {
            if (arg instanceof SassNull) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(CssSerializer.serializeValue(arg, false, false));
        }
        sb.append(')');
        return new SassString(sb.toString(), false);
    }

    /** Mix two colors with given weight (0-100). */
    private static SassColor mixGlobalColors(SassColor c1, SassColor c2, double weight) {
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

    /** Convert codepoint index to code unit index. */
    private static int codepointToCodeUnit(String text, int cpIndex) {
        int cuIndex = 0;
        for (int i = 0; i < cpIndex && cuIndex < text.length(); i++) {
            cuIndex += Character.isHighSurrogate(text.charAt(cuIndex)) ? 2 : 1;
        }
        return Math.min(cuIndex, text.length());
    }

    private static String getTypeOf(Value value) {
        if (value instanceof SassNumber) return "number";
        if (value instanceof SassString) return "string";
        if (value instanceof SassColor) return "color";
        if (value instanceof SassList) return "list";
        if (value instanceof SassMap) return "map";
        if (value instanceof SassBoolean) return "bool";
        if (value instanceof SassNull) return "null";
        if (value instanceof SassFunction) return "function";
        return "unknown";
    }

    // -----------------------------------------------------------------------
    // Statement visitors
    // -----------------------------------------------------------------------

    @Override
    public @Nullable Value visitStylesheet(Stylesheet node) {
        for (var child : node.getChildren()) {
            child.accept(this);
        }
        return null;
    }

    @Override
    public @Nullable Value visitStyleRule(StyleRule node) {
        var selectorText = performInterpolation(node.getSelector()).trim();

        // If we're inside another style rule, combine selectors.
        // Each parent selector must be combined with each child selector,
        // matching dart-sass's selector resolution behavior.
        boolean nested = styleRule != null;
        if (nested) {
            var parentSelector = styleRule.getSelector().getValue();
            selectorText = resolveNestedSelectors(parentSelector, selectorText);
        }

        var cssSelector = new CssValue<>(selectorText, node.getSelector().getSpan());
        var rule = new ModifiableCssStyleRule(cssSelector, node.getSpan());

        // Nested rules get added to the flatten target (normally the stylesheet root,
        // but the @media/@supports rule when bubbling) instead of the parent rule.
        var targetParent = nested ? styleFlattenTarget : parent;

        // Pre-add the rule to targetParent so that it appears before any nested
        // child rules that also get flattened into targetParent.
        // This matches dart-sass's source-order-preserving behavior.
        targetParent.addChild(rule);

        var oldParent = parent;
        var oldStyleRule = styleRule;
        parent = rule;
        styleRule = rule;
        try {
            for (var child : node.getChildren()) {
                child.accept(this);
            }
        } finally {
            parent = oldParent;
            styleRule = oldStyleRule;
        }

        // Remove if it ended up with no declarations (only nested rules)
        if (rule.getChildren().isEmpty()) {
            rule.remove();
        }

        // Mark the last child of the parent as a group end for blank line separation
        if (styleRule == null && !parent.getChildren().isEmpty()) {
            parent.getChildren().getLast().setGroupEnd(true);
        }

        return null;
    }

    @Override
    public @Nullable Value visitDeclaration(Declaration node) {
        var name = performInterpolation(node.getName());
        Value cssValue;
        if (node.getValue() != null) {
            cssValue = node.getValue().accept(this);
        } else {
            cssValue = SassNull.INSTANCE;
        }

        var cssName = new CssValue<>(name, node.getName().getSpan());
        var cssVal = new CssValue<>(cssValue, node.getSpan());
        var decl = new ModifiableCssDeclaration(
                cssName, cssVal, node.getSpan(), node.isParsedAsSassScript());
        parent.addChild(decl);

        // Handle nested declarations (e.g., font: { weight: bold; })
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (var child : node.getChildren()) {
                child.accept(this);
            }
        }

        return null;
    }

    @Override
    public @Nullable Value visitVariableDeclaration(VariableDeclaration node) {
        var value = node.getExpression().accept(this);

        if (node.isGuarded()) {
            var existing = node.isGlobal()
                    ? environment.getGlobalVariable(node.getName())
                    : environment.getVariable(node.getName());
            if (existing != null && existing != SassNull.INSTANCE) {
                return null;
            }
        }

        if (node.isGlobal()) {
            environment.setGlobalVariable(node.getName(), value);
        } else {
            environment.setVariable(node.getName(), value);
        }
        return null;
    }

    @Override
    public @Nullable Value visitIfRule(IfRule node) {
        for (var clause : node.getClauses()) {
            var condition = clause.getCondition().accept(this);
            if (isTruthy(condition)) {
                return handleStatements(clause.getChildren());
            }
        }
        if (node.getLastClause() != null) {
            return handleStatements(node.getLastClause().getChildren());
        }
        return null;
    }

    @Override
    public @Nullable Value visitForRule(ForRule node) {
        var fromVal = node.getFrom().accept(this).assertNumber(null);
        var toVal = node.getTo().accept(this).assertNumber(null);
        int from = fromVal.assertInt();
        int to = toVal.assertInt();

        if (node.isExclusive()) {
            // @for $i from X to Y (exclusive)
            int step = from <= to ? 1 : -1;
            for (int i = from; i != to; i += step) {
                var result = executeForIteration(node, i);
                if (result != null) return result;
            }
        } else {
            // @for $i from X through Y (inclusive)
            int step = from <= to ? 1 : -1;
            for (int i = from; ; i += step) {
                var result = executeForIteration(node, i);
                if (result != null) return result;
                if (i == to) break;
            }
        }
        return null;
    }

    private @Nullable Value executeForIteration(ForRule node, int i) {
        return environment.scope(() -> {
            environment.setLocalVariable(node.getVariable(), SassNumber.create(i));
            return handleStatements(node.getChildren());
        });
    }

    @Override
    public @Nullable Value visitEachRule(EachRule node) {
        var listVal = node.getList().accept(this);
        var list = listVal.asList();

        for (var element : list) {
            var result = environment.scope(() -> {
                setEachVariables(node.getVariables(), element);
                return handleStatements(node.getChildren());
            });
            if (result != null) return result;
        }
        return null;
    }

    private void setEachVariables(List<String> variables, Value value) {
        if (variables.size() == 1) {
            environment.setLocalVariable(variables.get(0), value);
        } else {
            var list = value.asList();
            for (int i = 0; i < variables.size(); i++) {
                environment.setLocalVariable(
                        variables.get(i),
                        i < list.size() ? list.get(i) : SassNull.INSTANCE);
            }
        }
    }

    @Override
    public @Nullable Value visitWhileRule(WhileRule node) {
        while (true) {
            var condition = node.getCondition().accept(this);
            if (!isTruthy(condition)) break;
            var result = handleStatements(node.getChildren());
            if (result != null) return result;
        }
        return null;
    }

    @Override
    public @Nullable Value visitFunctionRule(FunctionRule node) {
        var callable = new UserDefinedCallable(node, environment.closure(), false);
        environment.setFunction(node.getName(), callable);
        return null;
    }

    @Override
    public @Nullable Value visitMixinRule(MixinRule node) {
        var callable = new UserDefinedCallable(node, environment.closure(), false);
        environment.setMixin(node.getName(), callable);
        return null;
    }

    @Override
    public @Nullable Value visitIncludeRule(IncludeRule node) {
        var callable = environment.getMixin(node.getName(), node.getNamespace());
        if (callable == null) {
            throw new SassRuntimeException(
                    "Undefined mixin \"" + node.getOriginalName() + "\".",
                    node.getSpan());
        }

        if (callable instanceof UserDefinedCallable udc) {
            runUserDefinedCallable(node.getArguments(), udc, node.getContent(), () -> {
                var decl = (CallableDeclaration) udc.getDeclaration();
                for (var child : decl.getChildren()) {
                    child.accept(this);
                }
                return null;
            });
        } else if (callable instanceof BuiltInCallable bic) {
            runBuiltInCallable(node.getArguments(), bic);
        }
        return null;
    }

    @Override
    public @Nullable Value visitContentRule(ContentRule node) {
        var content = environment.getContent();
        if (content == null) return null;

        runUserDefinedCallable(
                node.getArguments(),
                new UserDefinedCallable(content.declaration(), content.environment(), false),
                null,
                () -> {
                    for (var child : content.declaration().getChildren()) {
                        child.accept(this);
                    }
                    return null;
                }
        );
        return null;
    }

    @Override
    public @Nullable Value visitContentBlock(ContentBlock node) {
        throw new SassRuntimeException(
                "This should not be visited directly.", node.getSpan());
    }

    @Override
    public @Nullable Value visitReturnRule(ReturnRule node) {
        throw new ReturnValue(node.getExpression().accept(this));
    }

    @Override
    public @Nullable Value visitLoudComment(LoudComment node) {
        var text = performInterpolation(node.getText());
        parent.addChild(new ModifiableCssComment(text, node.getSpan()));
        return null;
    }

    @Override
    public @Nullable Value visitSilentComment(SilentComment node) {
        // Silent comments are not emitted in CSS output.
        return null;
    }

    @Override
    public @Nullable Value visitDebugRule(DebugRule node) {
        var value = node.getExpression().accept(this);
        var message = value instanceof SassString s ? s.getText()
                : CssSerializer.serializeValue(value, true, true);
        System.err.println("DEBUG: " + message);
        return null;
    }

    @Override
    public @Nullable Value visitWarnRule(WarnRule node) {
        var value = node.getExpression().accept(this);
        var message = value instanceof SassString s ? s.getText()
                : CssSerializer.serializeValue(value, true, true);
        System.err.println("WARNING: " + message);
        return null;
    }

    @Override
    public @Nullable Value visitErrorRule(ErrorRule node) {
        var value = node.getExpression().accept(this);
        var message = value instanceof SassString s ? s.getText()
                : CssSerializer.serializeValue(value, true, true);
        throw new SassRuntimeException(message, node.getSpan());
    }

    @Override
    public @Nullable Value visitMediaRule(MediaRule node) {
        var queryText = performInterpolation(node.getQuery());
        var queries = List.of(new com.sass.ast.css.CssMediaQuery(List.of(queryText)));
        var mediaRule = new ModifiableCssMediaRule(queries, node.getSpan());

        if (styleRule != null) {
            // Bubble @media past the style rule: @media goes to the flatten target
            // (normally root), and children are wrapped in a copy of the parent style rule.
            var innerRule = styleRule.copyWithoutChildren();

            var oldParent = parent;
            var oldStyleRule = styleRule;
            var oldFlattenTarget = styleFlattenTarget;

            // Declarations go to innerRule; nested style rules flatten into mediaRule
            parent = innerRule;
            styleRule = innerRule;
            styleFlattenTarget = mediaRule;

            // Pre-add innerRule to mediaRule so declarations appear before nested rules
            mediaRule.addChild(innerRule);

            try {
                for (var child : node.getChildren()) {
                    child.accept(this);
                }
            } finally {
                parent = oldParent;
                styleRule = oldStyleRule;
                styleFlattenTarget = oldFlattenTarget;
            }

            // Remove innerRule if it ended up empty (no declarations)
            if (innerRule.getChildren().isEmpty()) {
                innerRule.remove();
            }

            // Add @media to the outer flatten target
            if (!mediaRule.getChildren().isEmpty()) {
                oldFlattenTarget.addChild(mediaRule);
            }
        } else {
            // Non-bubbling: set styleFlattenTarget so nested style rules
            // flatten into the @media rule, not into the stylesheet root.
            var oldFlattenTarget = styleFlattenTarget;
            styleFlattenTarget = mediaRule;
            try {
                withParent(mediaRule, () -> {
                    for (var child : node.getChildren()) {
                        child.accept(this);
                    }
                });
            } finally {
                styleFlattenTarget = oldFlattenTarget;
            }
        }
        return null;
    }

    @Override
    public @Nullable Value visitSupportsRule(SupportsRule node) {
        var conditionText = visitSupportsCondition(node.getCondition());
        var condition = new CssValue<>(conditionText, node.getSpan());
        var supportsRule = new ModifiableCssSupportsRule(condition, node.getSpan());

        if (styleRule != null) {
            // Bubble @supports past the style rule, same as @media bubbling
            var innerRule = styleRule.copyWithoutChildren();

            var oldParent = parent;
            var oldStyleRule = styleRule;
            var oldFlattenTarget = styleFlattenTarget;

            parent = innerRule;
            styleRule = innerRule;
            styleFlattenTarget = supportsRule;

            supportsRule.addChild(innerRule);

            try {
                for (var child : node.getChildren()) {
                    child.accept(this);
                }
            } finally {
                parent = oldParent;
                styleRule = oldStyleRule;
                styleFlattenTarget = oldFlattenTarget;
            }

            if (innerRule.getChildren().isEmpty()) {
                innerRule.remove();
            }

            if (!supportsRule.getChildren().isEmpty()) {
                oldFlattenTarget.addChild(supportsRule);
            }
        } else {
            // Non-bubbling: set styleFlattenTarget so nested style rules
            // flatten into the @supports rule, not into the stylesheet root.
            var oldFlattenTarget = styleFlattenTarget;
            styleFlattenTarget = supportsRule;
            try {
                withParent(supportsRule, () -> {
                    for (var child : node.getChildren()) {
                        child.accept(this);
                    }
                });
            } finally {
                styleFlattenTarget = oldFlattenTarget;
            }
        }
        return null;
    }

    @Override
    public @Nullable Value visitAtRule(AtRule node) {
        var name = performInterpolation(node.getName());
        @Nullable String valueText = null;
        if (node.getValue() != null) {
            valueText = performInterpolation(node.getValue());
        }

        var cssName = new CssValue<>(name, node.getName().getSpan());
        var cssValue = valueText != null
                ? new CssValue<>(valueText, node.getValue().getSpan())
                : null;

        if (node.getChildren() == null) {
            // Childless at-rule
            var rule = new ModifiableCssAtRule(cssName, cssValue, true, node.getSpan());
            parent.addChild(rule);
        } else if (styleRule != null) {
            // Bubble: at-rule (@keyframes, @font-face, etc.) passes through style rules
            // to the flatten target. Unlike @media/@supports, generic at-rules don't wrap
            // their content in a copy of the parent style rule.
            var rule = new ModifiableCssAtRule(cssName, cssValue, false, node.getSpan());

            var oldParent = parent;
            var oldStyleRule = styleRule;

            parent = rule;
            styleRule = null; // children are at-rule-scoped, not nested in a style rule

            try {
                for (var child : node.getChildren()) {
                    child.accept(this);
                }
            } finally {
                parent = oldParent;
                styleRule = oldStyleRule;
            }

            if (!rule.getChildren().isEmpty()) {
                styleFlattenTarget.addChild(rule);
            }
        } else {
            // Non-bubbling: set styleFlattenTarget so nested style rules
            // flatten into the at-rule, not into the stylesheet root.
            var rule = new ModifiableCssAtRule(cssName, cssValue, false, node.getSpan());
            var oldFlattenTarget = styleFlattenTarget;
            styleFlattenTarget = rule;
            try {
                withParent(rule, () -> {
                    for (var child : node.getChildren()) {
                        child.accept(this);
                    }
                });
            } finally {
                styleFlattenTarget = oldFlattenTarget;
            }
        }
        return null;
    }

    @Override
    public @Nullable Value visitAtRootRule(AtRootRule node) {
        // Simplified: just emit children at the current level
        for (var child : node.getChildren()) {
            child.accept(this);
        }
        return null;
    }

    @Override
    public @Nullable Value visitExtendRule(ExtendRule node) {
        // @extend is handled in Phase 8
        return null;
    }

    @Override
    public @Nullable Value visitImportRule(ImportRule node) {
        for (var import_ : node.getImports()) {
            if (import_ instanceof StaticImport si) {
                var url = performInterpolation(si.getUrl());
                var cssUrl = new CssValue<>(url, si.getSpan());
                parent.addChild(new ModifiableCssImport(cssUrl, si.getSpan()));
            } else if (import_ instanceof DynamicImport di) {
                visitDynamicImport(di);
            }
        }
        return null;
    }

    /**
     * Resolves and evaluates a dynamic @import, inlining the imported
     * stylesheet's content into the current CSS tree.
     *
     * <p>@import shares scope with the importing file: variables, mixins, and
     * functions defined in the imported file are visible to the importing file.</p>
     */
    private void visitDynamicImport(DynamicImport import_) {
        if (importer == null) {
            throw new SassRuntimeException(
                    "Can't find stylesheet to import.", import_.getSpan());
        }

        var url = import_.getUrlString();
        try {
            var result = importer.resolve(url, currentSourceUri);
            if (result == null) {
                throw new SassRuntimeException(
                        "Can't find stylesheet to import.", import_.getSpan());
            }

            URI resolvedUri = result.sourceUri();

            // Circular import detection
            if (activeImports.contains(resolvedUri)) {
                throw new SassRuntimeException(
                        "This file is already being loaded.", import_.getSpan());
            }

            // Parse (with caching)
            Stylesheet importedStylesheet = null;
            if (importCache != null) {
                importedStylesheet = importCache.getParsedStylesheet(result.path());
            }
            if (importedStylesheet == null) {
                var parser = new ScssParser(result.contents(), resolvedUri);
                importedStylesheet = parser.parse();
                if (importCache != null) {
                    importCache.putParsedStylesheet(result.path(), importedStylesheet);
                }
            }

            // Evaluate in current environment (shared scope for @import)
            var oldSourceUri = currentSourceUri;
            currentSourceUri = resolvedUri;
            activeImports.add(resolvedUri);
            try {
                for (var child : importedStylesheet.getChildren()) {
                    child.accept(this);
                }
            } finally {
                currentSourceUri = oldSourceUri;
                activeImports.remove(resolvedUri);
            }
        } catch (java.io.IOException e) {
            throw new SassRuntimeException(
                    "Error reading " + url + ": " + e.getMessage(), import_.getSpan());
        }
    }

    @Override
    public @Nullable Value visitUseRule(UseRule node) {
        var url = node.getUrl();

        // Handle built-in sass:* modules
        if (BuiltInModule.isBuiltIn(url)) {
            var moduleName = BuiltInModule.extractName(url);
            var builtIn = BuiltInModule.get(moduleName);
            if (builtIn == null) {
                throw new SassRuntimeException(
                        "Unknown built-in module \"" + url + "\".", node.getSpan());
            }
            String namespace = node.getNamespace();
            environment.addModule(builtIn, namespace);
            return null;
        }

        if (importer == null) {
            throw new SassRuntimeException(
                    "Can't find stylesheet to import.", node.getSpan());
        }

        // Evaluate configuration values before loading the module
        Map<String, Value> configValues = null;
        if (!node.getConfiguration().isEmpty()) {
            configValues = new LinkedHashMap<>();
            for (var config : node.getConfiguration()) {
                configValues.put(config.getName(), config.getExpression().accept(this));
            }
        }

        // Load the module with configuration (deduplicates by URI)
        var module = loadModule(url, node.getSpan(), node.getConfiguration(), configValues);

        if (module != null) {
            // The parser already resolves the namespace:
            // - @use 'foo' as bar  → namespace = "bar"
            // - @use 'foo'         → namespace = default from URL (e.g. "foo")
            // - @use 'foo' as *    → namespace = null (global/no namespace)
            String namespace = node.getNamespace();
            environment.addModule(module, namespace);

            // Inline the module's CSS output into the current tree
            inlineModuleCss(module);
        }
        return null;
    }

    @Override
    public @Nullable Value visitForwardRule(ForwardRule node) {
        var url = node.getUrl();

        // Handle built-in sass:* modules
        if (BuiltInModule.isBuiltIn(url)) {
            var moduleName = BuiltInModule.extractName(url);
            var builtIn = BuiltInModule.get(moduleName);
            if (builtIn == null) {
                throw new SassRuntimeException(
                        "Unknown built-in module \"" + url + "\".", node.getSpan());
            }
            var forwarded = builtIn.forward(
                    node.getShownVariables(),
                    node.getShownMixinsAndFunctions(),
                    node.getHiddenVariables(),
                    node.getHiddenMixinsAndFunctions(),
                    node.getPrefix());
            environment.forwardModule(forwarded);
            return null;
        }

        if (importer == null) {
            throw new SassRuntimeException(
                    "Can't find stylesheet to import.", node.getSpan());
        }

        var module = loadModule(url, node.getSpan(), List.of(), null);
        if (module != null) {
            // Create a filtered view of the module
            var forwarded = module.forward(
                    node.getShownVariables(),
                    node.getShownMixinsAndFunctions(),
                    node.getHiddenVariables(),
                    node.getHiddenMixinsAndFunctions(),
                    node.getPrefix());
            environment.forwardModule(forwarded);

            // @forward also includes the forwarded module's CSS in the output,
            // just like @use. The dedup check in inlineModuleCss ensures each
            // module's CSS is only included once.
            inlineModuleCss(module);
        }
        return null;
    }

    /**
     * Loads a module by URL, returning a cached module if already loaded.
     * Executes the module in an isolated environment if it's a new load.
     *
     * @param url the URL to resolve
     * @param span for error reporting
     * @param configDecls the @use with (...) configuration declarations (for !default handling)
     * @param configValues pre-evaluated configuration values, keyed by variable name (without $)
     */
    private @Nullable Module loadModule(
            String url, FileSpan span,
            List<ConfiguredVariable> configDecls,
            @Nullable Map<String, Value> configValues) {
        try {
            var result = importer.resolve(url, currentSourceUri);
            if (result == null) {
                throw new SassRuntimeException(
                        "Can't find stylesheet to import.", span);
            }

            URI resolvedUri = result.sourceUri();

            // Check if already loaded (modules are singletons per URI)
            var existing = loadedModules.get(resolvedUri);
            if (existing != null) return existing;

            // Circular detection
            if (activeImports.contains(resolvedUri)) {
                throw new SassRuntimeException(
                        "This file is already being loaded.", span);
            }

            // Parse
            Stylesheet stylesheet = null;
            if (importCache != null) {
                stylesheet = importCache.getParsedStylesheet(result.path());
            }
            if (stylesheet == null) {
                var parser = new ScssParser(result.contents(), resolvedUri);
                stylesheet = parser.parse();
                if (importCache != null) {
                    importCache.putParsedStylesheet(result.path(), stylesheet);
                }
            }

            // Execute in isolated environment
            var moduleEnv = new Environment();

            // Apply configuration values BEFORE execution so !default works
            if (configValues != null && !configValues.isEmpty()) {
                for (var entry : configValues.entrySet()) {
                    moduleEnv.setGlobalVariable(entry.getKey(), entry.getValue());
                }
            }

            var moduleDummySpan = stylesheet.getSpan();
            var moduleRoot = new ModifiableCssStylesheet(moduleDummySpan);

            // Save and swap evaluation context
            var oldEnv = swapEnvironment(moduleEnv);
            var oldParent = parent;
            var oldRoot = root;
            var oldFlattenTarget = styleFlattenTarget;
            var oldStyleRule = styleRule;
            var oldSourceUri = currentSourceUri;
            parent = moduleRoot;
            root = moduleRoot;
            styleFlattenTarget = moduleRoot;
            styleRule = null;
            currentSourceUri = resolvedUri;
            activeImports.add(resolvedUri);

            try {
                for (var child : stylesheet.getChildren()) {
                    child.accept(this);
                }
            } finally {
                activeImports.remove(resolvedUri);
                currentSourceUri = oldSourceUri;
                styleRule = oldStyleRule;
                styleFlattenTarget = oldFlattenTarget;
                root = oldRoot;
                parent = oldParent;
                restoreEnvironment(oldEnv);
            }

            // Build module from the isolated environment's global scope
            var module = new Module(
                    resolvedUri,
                    moduleEnv.getGlobalVariables(),
                    moduleEnv.getGlobalFunctions(),
                    moduleEnv.getGlobalMixins(),
                    moduleRoot);

            // Also include forwarded module members
            for (var fwd : moduleEnv.getForwardedModules()) {
                module.getVariables().putAll(fwd.getVariables());
                module.getFunctions().putAll(fwd.getFunctions());
                module.getMixins().putAll(fwd.getMixins());
            }

            loadedModules.put(resolvedUri, module);
            return module;
        } catch (java.io.IOException e) {
            throw new SassRuntimeException(
                    "Error reading " + url + ": " + e.getMessage(), span);
        }
    }

    /**
     * Inlines a module's CSS output into the current stylesheet.
     * Each module's CSS is only included once (matching dart-sass behavior).
     */
    private void inlineModuleCss(Module module) {
        var uri = module.getUrl();
        if (uri != null && !inlinedModuleCss.add(uri)) {
            // Already inlined this module's CSS — skip duplicate inclusion
            return;
        }
        for (var child : module.getCss().getChildren()) {
            root.addChild(child);
        }
    }

    /**
     * Resolves nested selectors by combining each parent selector with each
     * child selector, matching dart-sass's selector resolution behavior.
     *
     * <p>Given parent {@code ".a, .b"} and child {@code ".c, .d"}, produces
     * {@code ".a .c, .a .d, .b .c, .b .d"}. Handles {@code &} (parent selector
     * reference) by replacing it in each child selector individually.</p>
     */
    private static String resolveNestedSelectors(String parentSelector, String childSelector) {
        // Split into individual selectors, respecting parentheses
        var parents = splitSelectors(parentSelector);
        var children = splitSelectors(childSelector);

        var result = new StringBuilder();
        boolean first = true;
        for (var parent : parents) {
            var trimmedParent = parent.trim();
            for (var child : children) {
                var trimmedChild = child.trim();
                if (!first) result.append(", ");
                first = false;

                if (trimmedChild.contains("&")) {
                    result.append(trimmedChild.replace("&", trimmedParent));
                } else {
                    result.append(trimmedParent).append(' ').append(trimmedChild);
                }
            }
        }
        return result.toString();
    }

    /**
     * Splits a selector string by commas, but respects parentheses and brackets
     * so that {@code ":not(.a, .b), .c"} splits into {@code [":not(.a, .b)", ".c"]}.
     */
    private static List<String> splitSelectors(String selector) {
        var result = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(selector.substring(start, i));
                start = i + 1;
            }
        }
        result.add(selector.substring(start));
        return result;
    }

    /**
     * Derives a default namespace from a URL.
     * e.g., "foo/bar" -> "bar", "foo/_bar.scss" -> "bar"
     */
    private static String defaultNamespace(String url) {
        // Strip extension
        var lastSlash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
        var basename = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        // Strip leading underscore
        if (basename.startsWith("_")) basename = basename.substring(1);
        // Strip extension
        var dot = basename.lastIndexOf('.');
        if (dot > 0) basename = basename.substring(0, dot);
        return basename;
    }

    /**
     * Swaps the current environment with a new one, returning the old one.
     */
    private Environment swapEnvironment(Environment newEnv) {
        var old = this.environment;
        this.environment = newEnv;
        return old;
    }

    /**
     * Restores a previously saved environment.
     */
    private void restoreEnvironment(Environment oldEnv) {
        this.environment = oldEnv;
    }

    // -----------------------------------------------------------------------
    // Expression visitors
    // -----------------------------------------------------------------------

    @Override
    public Value visitBinaryOperationExpression(BinaryOperationExpression node) {
        var left = node.getLeft().accept(this);
        var operator = node.getOperator();

        // Short-circuit for boolean operators
        if (operator == BinaryOperator.AND) {
            return isTruthy(left) ? node.getRight().accept(this) : left;
        }
        if (operator == BinaryOperator.OR) {
            return isTruthy(left) ? left : node.getRight().accept(this);
        }

        var right = node.getRight().accept(this);

        return switch (operator) {
            case EQUALS -> SassBoolean.of(left.equals(right));
            case NOT_EQUALS -> SassBoolean.of(!left.equals(right));
            case PLUS -> left.plus(right);
            case MINUS -> left.minus(right);
            case TIMES -> left.times(right);
            case DIVIDED_BY -> left.dividedBy(right);
            case MODULO -> left.modulo(right);
            case GREATER_THAN -> left.greaterThan(right);
            case GREATER_THAN_OR_EQUALS -> left.greaterThanOrEquals(right);
            case LESS_THAN -> left.lessThan(right);
            case LESS_THAN_OR_EQUALS -> left.lessThanOrEquals(right);
            case SINGLE_EQUALS -> new SassString(
                    toCss(left) + "=" + toCss(right), false);
            default -> throw new SassRuntimeException(
                    "Unknown operator " + operator, node.getSpan());
        };
    }

    @Override
    public Value visitUnaryOperationExpression(UnaryOperationExpression node) {
        var operand = node.getOperand().accept(this);
        return switch (node.getOperator()) {
            case PLUS -> operand.unaryPlus();
            case MINUS -> operand.unaryMinus();
            case NOT -> operand.unaryNot();
            case DIVIDE -> operand.unaryDivide();
        };
    }

    @Override
    public Value visitBooleanExpression(BooleanExpression node) {
        return SassBoolean.of(node.getValue());
    }

    @Override
    public Value visitColorExpression(ColorExpression node) {
        return node.getValue();
    }

    @Override
    public Value visitNumberExpression(NumberExpression node) {
        if (node.getUnit() != null) {
            return SassNumber.create(node.getValue(), node.getUnit());
        }
        return SassNumber.create(node.getValue());
    }

    @Override
    public Value visitNullExpression(NullExpression node) {
        return SassNull.INSTANCE;
    }

    @Override
    public Value visitStringExpression(StringExpression node) {
        var text = performInterpolation(node.getText());
        return new SassString(text, node.hasQuotes());
    }

    @Override
    public Value visitListExpression(ListExpression node) {
        var contents = new ArrayList<Value>(node.getContents().size());
        for (var expr : node.getContents()) {
            contents.add(expr.accept(this));
        }
        return new SassList(contents, node.getSeparator(), node.hasBrackets());
    }

    @Override
    public Value visitMapExpression(MapExpression node) {
        var map = new LinkedHashMap<Value, Value>();
        for (var pair : node.getPairs()) {
            var key = pair.key().accept(this);
            var value = pair.value().accept(this);
            if (map.containsKey(key)) {
                throw new SassRuntimeException(
                        "Duplicate key " + toCss(key) + ".", node.getSpan());
            }
            map.put(key, value);
        }
        return new SassMap(map);
    }

    @Override
    public Value visitVariableExpression(VariableExpression node) {
        var value = environment.getVariable(node.getName(), node.getNamespace());
        if (value == null) {
            throw new SassRuntimeException(
                    "Undefined variable \"$" + node.getName() + "\".",
                    node.getSpan());
        }
        return value;
    }

    @Override
    public Value visitParenthesizedExpression(ParenthesizedExpression node) {
        return node.getExpression().accept(this);
    }

    @Override
    public Value visitSelectorExpression(SelectorExpression node) {
        if (styleRule != null) {
            return new SassString(styleRule.getSelector().getValue(), false);
        }
        return SassNull.INSTANCE;
    }

    @Override
    public Value visitValueExpression(ValueExpression node) {
        return node.getValue();
    }

    @Override
    public Value visitFunctionExpression(FunctionExpression node) {
        // First check user-defined and module functions (with namespace support)
        var callable = environment.getFunction(node.getName(), node.getNamespace());
        if (callable instanceof UserDefinedCallable udc) {
            return runUserDefinedFunction(node.getArguments(), udc, node.getSpan());
        }
        if (callable instanceof BuiltInCallable bic) {
            return runBuiltInCallable(node.getArguments(), bic);
        }

        // If a namespace was specified and we didn't find it, that's an error
        if (node.getNamespace() != null) {
            throw new SassRuntimeException(
                    "Undefined function \"" + node.getNamespace() + "." + node.getName() + "\".",
                    node.getSpan());
        }

        // Then check global built-in functions
        var builtIn = builtInFunctions.get(node.getName());
        if (builtIn != null) {
            return runBuiltInCallable(node.getArguments(), builtIn);
        }

        // Handle CSS calculation functions (calc, min, max, clamp)
        String lowerName = node.getName().toLowerCase();
        if (lowerName.equals("calc") || lowerName.equals("-webkit-calc")
                || lowerName.equals("-moz-calc")) {
            return visitCalculation(node);
        }

        // Fall back to plain CSS function call
        return visitPlainCssFunction(node);
    }

    @Override
    public Value visitInterpolatedFunctionExpression(InterpolatedFunctionExpression node) {
        var name = performInterpolation(node.getName());
        // Treat as a plain CSS function — preserve quotes on arguments
        var argStrings = new ArrayList<String>();
        for (var arg : node.getArguments().getPositional()) {
            argStrings.add(toCssQuoted(arg.accept(this)));
        }
        return new SassString(
                name + "(" + String.join(", ", argStrings) + ")", false);
    }

    @Override
    public Value visitIfExpression(IfExpression node) {
        var args = evaluateArguments(node.getArguments());
        if (args.size() < 3) {
            throw new SassRuntimeException(
                    "if() requires exactly 3 arguments.", node.getSpan());
        }
        return isTruthy(args.get(0)) ? args.get(1) : args.get(2);
    }

    @Override
    public Value visitSupportsExpression(SupportsExpression node) {
        return new SassString(visitSupportsCondition(node.getCondition()), false);
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /** Performs string interpolation on an Interpolation node. */
    public String performInterpolation(Interpolation interpolation) {
        var sb = new StringBuilder();
        for (var part : interpolation.getContents()) {
            if (part instanceof String s) {
                sb.append(s);
            } else if (part instanceof Expression expr) {
                var value = expr.accept(this);
                sb.append(toCss(value));
            }
        }
        return sb.toString();
    }

    /**
     * Converts a value to its CSS string representation without quoting strings.
     * Used in interpolation contexts where string quotes should be stripped.
     */
    private String toCss(Value value) {
        if (value instanceof SassNull) return "";
        return CssSerializer.serializeValue(value, false, false);
    }

    /**
     * Converts a value to its CSS string representation, preserving string quotes.
     * Used in function call argument contexts (e.g., {@code url("data:...")}).
     * Matches dart-sass's {@code _serialize(value, node, quote: true)}.
     */
    private String toCssQuoted(Value value) {
        if (value instanceof SassNull) return "";
        return CssSerializer.serializeValue(value, false, true);
    }

    /** Returns whether a value is truthy (not false and not null). */
    private static boolean isTruthy(Value value) {
        return value != SassBoolean.FALSE && value != SassNull.INSTANCE;
    }

    /**
     * Adds a parent node to the CSS tree, runs the callback to populate
     * its children, then restores the previous parent.
     */
    private void withParent(ModifiableCssParentNode newParent, Runnable callback) {
        var oldParent = parent;
        parent = newParent;
        try {
            callback.run();
        } finally {
            parent = oldParent;
        }
        if (!newParent.getChildren().isEmpty()) {
            oldParent.addChild(newParent);
        }
    }

    /** Handles a list of statements and returns any early-exit value (@return). */
    private @Nullable Value handleStatements(List<Statement> statements) {
        for (var statement : statements) {
            var result = statement.accept(this);
            if (result != null) return result;
        }
        return null;
    }

    /** Evaluates all positional arguments to values. */
    private List<Value> evaluateArguments(ArgumentInvocation invocation) {
        var result = new ArrayList<Value>();
        for (var expr : invocation.getPositional()) {
            result.add(expr.accept(this));
        }
        // Also handle named arguments by adding them in order
        for (var entry : invocation.getNamed().entrySet()) {
            result.add(entry.getValue().accept(this));
        }
        return result;
    }

    /**
     * Runs a user-defined callable (function or mixin), binding arguments to
     * the callable's parameter declaration.
     */
    private <T> T runUserDefinedCallable(
            ArgumentInvocation invocation,
            UserDefinedCallable callable,
            @Nullable ContentBlock contentBlock,
            java.util.function.Supplier<T> run) {

        var decl = callable.getDeclaration();
        var params = decl.getParameters();

        // Evaluate all arguments
        var positionalValues = new ArrayList<Value>();
        for (var expr : invocation.getPositional()) {
            positionalValues.add(expr.accept(this));
        }
        var namedValues = new HashMap<String, Value>();
        for (var entry : invocation.getNamed().entrySet()) {
            namedValues.put(entry.getKey(), entry.getValue().accept(this));
        }

        // Save and switch to the callable's closure environment
        var oldEnv = switchEnvironment(callable.getEnvironment().closure());
        var oldContent = environment.getContent();

        try {
            return environment.scope(() -> {
                // Bind positional arguments
                var declArgs = params.getArguments();
                for (int i = 0; i < declArgs.size(); i++) {
                    var param = declArgs.get(i);
                    Value value;
                    if (i < positionalValues.size()) {
                        value = positionalValues.get(i);
                    } else if (namedValues.containsKey(param.getName())) {
                        value = namedValues.get(param.getName());
                    } else if (param.getDefaultValue() != null) {
                        value = param.getDefaultValue().accept(EvaluateVisitor.this);
                    } else {
                        throw new SassRuntimeException(
                                "Missing argument $" + param.getName() + ".",
                                invocation.getSpan());
                    }
                    environment.setLocalVariable(param.getName(), value);
                }

                // Handle rest argument
                if (params.getRestArgument() != null) {
                    var restValues = new ArrayList<Value>();
                    for (int i = declArgs.size(); i < positionalValues.size(); i++) {
                        restValues.add(positionalValues.get(i));
                    }
                    environment.setLocalVariable(
                            params.getRestArgument(),
                            new SassList(restValues, ListSeparator.COMMA));
                }

                // Set @content if provided
                if (contentBlock != null) {
                    environment.setContent(new Environment.UserDefinedContent(
                            contentBlock, oldEnv));
                } else {
                    environment.setContent(null);
                }

                return run.get();
            });
        } finally {
            switchEnvironment(oldEnv);
            environment.setContent(oldContent);
        }
    }

    /** Runs a user-defined function, returning its @return value. */
    private Value runUserDefinedFunction(
            ArgumentInvocation invocation,
            UserDefinedCallable callable,
            FileSpan span) {
        try {
            runUserDefinedCallable(invocation, callable, null, () -> {
                var decl = (CallableDeclaration) callable.getDeclaration();
                for (var child : decl.getChildren()) {
                    child.accept(this);
                }
                return null;
            });
        } catch (ReturnValue rv) {
            return rv.value;
        }
        throw new SassRuntimeException(
                "Function \"" + callable.getName() + "\" didn't return a value.",
                span);
    }

    /** Runs a built-in callable with the given arguments. */
    private Value runBuiltInCallable(ArgumentInvocation invocation, BuiltInCallable callable) {
        // Evaluate arguments
        var positionalValues = new ArrayList<Value>();
        for (var expr : invocation.getPositional()) {
            positionalValues.add(expr.accept(this));
        }
        var namedValues = new HashMap<String, Value>();
        for (var entry : invocation.getNamed().entrySet()) {
            namedValues.put(entry.getKey(), entry.getValue().accept(this));
        }

        var overload = callable.callbackFor(positionalValues.size(), namedValues.keySet());
        var params = overload.parameters();

        // Build the argument list in declared order
        var args = new ArrayList<Value>();
        var declArgs = params.getArguments();
        for (int i = 0; i < declArgs.size(); i++) {
            var param = declArgs.get(i);
            if (i < positionalValues.size()) {
                args.add(positionalValues.get(i));
            } else if (namedValues.containsKey(param.getName())) {
                args.add(namedValues.get(param.getName()));
            } else {
                args.add(SassNull.INSTANCE); // default
            }
        }
        // Append rest arguments
        for (int i = declArgs.size(); i < positionalValues.size(); i++) {
            args.add(positionalValues.get(i));
        }

        try {
            return overload.callback().apply(args);
        } catch (SassScriptException e) {
            throw new SassRuntimeException(e.getMessage(), invocation.getSpan());
        }
    }

    /** Visits a plain CSS function call (not a Sass function). */
    private Value visitPlainCssFunction(FunctionExpression node) {
        var argStrings = new ArrayList<String>();
        for (var arg : node.getArguments().getPositional()) {
            var value = arg.accept(this);
            argStrings.add(toCssQuoted(value));
        }
        for (var entry : node.getArguments().getNamed().entrySet()) {
            var value = entry.getValue().accept(this);
            argStrings.add(toCssQuoted(value));
        }
        return new SassString(
                node.getOriginalName() + "(" + String.join(", ", argStrings) + ")",
                false);
    }

    /**
     * Handles a CSS calc() (or -webkit-calc, -moz-calc) function call.
     *
     * <p>Tries to fully evaluate the expression inside calc(). If all values
     * are Sass-evaluable (variables, function calls, compatible-unit arithmetic),
     * the result is simplified to a plain SassNumber. If evaluation fails (e.g.,
     * incompatible units like {@code 100% - 3rem}), falls back to producing a
     * {@link SassCalculation} value with the sub-expressions evaluated individually
     * and operators preserved as CSS calc operations.</p>
     */
    private Value visitCalculation(FunctionExpression node) {
        var args = node.getArguments().getPositional();
        if (args.isEmpty()) {
            return new SassString(node.getOriginalName() + "()", false);
        }
        // calc() takes a single argument (the mathematical expression)
        Expression arg = args.get(0);
        try {
            var result = arg.accept(this);
            if (result instanceof SassNumber) {
                return result; // Simplify: calc(42px) → 42px
            }
            if (result instanceof SassCalculation) {
                return result; // Already a calculation (nested calc)
            }
            // Not a number — wrap in calc() as a string
            return new SassString(node.getOriginalName() + "(" + toCss(result) + ")", false);
        } catch (SassRuntimeException | SassScriptException e) {
            // Evaluation failed (incompatible units, etc.)
            // Walk the expression tree: evaluate leaf nodes, preserve operators as calc operations
            Object calcArg = buildCalcArg(arg);
            return SassCalculation.calc(calcArg);
        }
    }

    /**
     * Recursively builds a calculation argument from a Sass expression tree.
     * <p>Binary operations become {@link SassCalculation.CalculationOperation} nodes.
     * Leaf expressions (numbers, variables, function calls) are evaluated.</p>
     */
    private Object buildCalcArg(Expression expr) {
        if (expr instanceof BinaryOperationExpression bop) {
            var op = bop.getOperator();
            // Only the standard calc operators (+, -, *, /) are valid in CSS calc
            if (op == BinaryOperator.PLUS || op == BinaryOperator.MINUS
                    || op == BinaryOperator.TIMES || op == BinaryOperator.DIVIDED_BY) {
                // First try to evaluate the whole binary operation
                try {
                    var result = expr.accept(this);
                    if (result instanceof SassNumber) return result;
                    return new SassCalculation.CalculationInterpolation(toCss(result));
                } catch (SassRuntimeException | SassScriptException e) {
                    // Can't simplify — build sub-arguments separately
                    Object left = buildCalcArg(bop.getLeft());
                    Object right = buildCalcArg(bop.getRight());
                    String operator = switch (op) {
                        case PLUS -> "+";
                        case MINUS -> "-";
                        case TIMES -> "*";
                        case DIVIDED_BY -> "/";
                        default -> op.getOperator();
                    };
                    return new SassCalculation.CalculationOperation(operator, left, right);
                }
            }
        }
        if (expr instanceof ParenthesizedExpression pe) {
            return buildCalcArg(pe.getExpression());
        }
        // For everything else (numbers, variables, function calls, unary ops):
        // evaluate normally
        try {
            var value = expr.accept(this);
            if (value instanceof SassNumber || value instanceof SassCalculation) {
                return value;
            }
            // Strings, colors, etc. — wrap as interpolation text
            return new SassCalculation.CalculationInterpolation(toCss(value));
        } catch (Exception e) {
            // Last resort: use the source text
            return new SassCalculation.CalculationInterpolation(expr.getSpan().text());
        }
    }

    /** Evaluates a @supports condition to a string. */
    private String visitSupportsCondition(SupportsCondition condition) {
        if (condition instanceof SupportsDeclaration sd) {
            var name = toCss(sd.getName().accept(this));
            var value = toCss(sd.getValue().accept(this));
            return "(" + name + ": " + value + ")";
        }
        if (condition instanceof SupportsNegation sn) {
            return "not " + visitSupportsCondition(sn.getCondition());
        }
        if (condition instanceof SupportsOperation so) {
            return visitSupportsCondition(so.getLeft()) + " " +
                    so.getOperator().name().toLowerCase() + " " +
                    visitSupportsCondition(so.getRight());
        }
        if (condition instanceof SupportsInterpolation si) {
            var value = si.getExpression().accept(this);
            return toCss(value);
        }
        if (condition instanceof SupportsAnything sa) {
            return "(" + performInterpolation(sa.getContents()) + ")";
        }
        if (condition instanceof SupportsFunction sf) {
            var name = performInterpolation(sf.getName());
            var args = performInterpolation(sf.getArguments());
            return name + "(" + args + ")";
        }
        return condition.toString();
    }

    /**
     * Switches to a different environment and returns the old one.
     * This is used for executing closures (mixin/function calls).
     */
    private Environment switchEnvironment(Environment newEnv) {
        var old = this.environment;
        this.environment = newEnv;
        return old;
    }

    private static FileSpan createDummySpan() {
        var file = new SourceFile("", null);
        var loc = new com.sass.util.SourceLocation(0, 0, 0, file);
        return new FileSpan(loc, loc, file);
    }
}
