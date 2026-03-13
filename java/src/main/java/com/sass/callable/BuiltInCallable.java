package com.sass.callable;

import com.sass.ast.sass.Argument;
import com.sass.ast.sass.ArgumentDeclaration;
import com.sass.util.FileSpan;
import com.sass.util.SourceFile;
import com.sass.value.SassNull;
import com.sass.value.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A callable defined in Java code.
 *
 * <p>Unlike user-defined callables, built-in callables support overloads. They
 * may declare multiple different callbacks with multiple different sets of
 * parameters. When the callable is invoked, the first callback with matching
 * parameters is invoked.
 */
public final class BuiltInCallable implements SassCallable {

    private final String name;
    private final List<Overload> overloads;
    private final boolean acceptsContent;

    /**
     * An overload consisting of a parameter declaration and a callback.
     */
    public record Overload(ArgumentDeclaration parameters,
                           Function<List<Value>, Value> callback) {
    }

    private BuiltInCallable(String name, List<Overload> overloads, boolean acceptsContent) {
        this.name = name;
        this.overloads = List.copyOf(overloads);
        this.acceptsContent = acceptsContent;
    }

    /**
     * Creates a function with a single parameter declaration and a single callback.
     *
     * <p>The parameter declaration is parsed from {@code parameters}, which should not
     * include parentheses. For example: {@code "$color, $amount"}.
     *
     * @param name the function name
     * @param parameters the parameter declaration string (e.g., "$x, $y: 0")
     * @param callback the function implementation
     * @return a new BuiltInCallable
     */
    public static BuiltInCallable function(String name, String parameters,
                                            Function<List<Value>, Value> callback) {
        return new BuiltInCallable(
                name,
                List.of(new Overload(parseParameters(name, parameters), callback)),
                false
        );
    }

    /**
     * Creates a mixin with a single parameter declaration and a single callback.
     *
     * <p>The parameter declaration is parsed from {@code parameters}, which should not
     * include parentheses.
     *
     * @param name the mixin name
     * @param parameters the parameter declaration string
     * @param callback the mixin implementation (returns void; callback receives args)
     * @return a new BuiltInCallable
     */
    public static BuiltInCallable mixin(String name, String parameters,
                                         Consumer<List<Value>> callback) {
        return mixin(name, parameters, callback, false);
    }

    /**
     * Creates a mixin with a single parameter declaration and a single callback.
     *
     * @param name the mixin name
     * @param parameters the parameter declaration string
     * @param callback the mixin implementation
     * @param acceptsContent whether this mixin accepts a content block
     * @return a new BuiltInCallable
     */
    public static BuiltInCallable mixin(String name, String parameters,
                                         Consumer<List<Value>> callback,
                                         boolean acceptsContent) {
        return new BuiltInCallable(
                name,
                List.of(new Overload(parseParameters(name, parameters), args -> {
                    callback.accept(args);
                    return SassNull.INSTANCE;
                })),
                acceptsContent
        );
    }

    /**
     * Creates a callable with a pre-parsed parameter declaration and a single callback.
     *
     * @param name the callable name
     * @param parameters the pre-parsed parameter declaration
     * @param callback the callable implementation
     * @return a new BuiltInCallable
     */
    public static BuiltInCallable parsed(String name, ArgumentDeclaration parameters,
                                          Function<List<Value>, Value> callback) {
        return new BuiltInCallable(
                name,
                List.of(new Overload(parameters, callback)),
                false
        );
    }

    /**
     * Creates a function with multiple implementations.
     *
     * <p>Each key/value pair in {@code overloads} defines the parameter declaration
     * for the overload (which should not include parentheses), and the callback to
     * execute if that parameter declaration matches.
     *
     * @param name the function name
     * @param overloads a map from parameter strings to callbacks
     * @return a new BuiltInCallable
     */
    public static BuiltInCallable overloadedFunction(
            String name,
            Map<String, Function<List<Value>, Value>> overloads) {
        var overloadList = new ArrayList<Overload>();
        for (var entry : overloads.entrySet()) {
            overloadList.add(new Overload(
                    parseParameters(name, entry.getKey()),
                    entry.getValue()
            ));
        }
        return new BuiltInCallable(name, overloadList, false);
    }

    /**
     * Returns the parameter declaration and callback for the given positional
     * and named parameters.
     *
     * <p>If no exact match is found, finds the closest approximation. Note that this
     * doesn't guarantee that {@code positionalCount} and {@code namedNames} are
     * valid for the returned {@link ArgumentDeclaration}.
     *
     * @param positionalCount the number of positional arguments
     * @param namedNames the set of named argument names
     * @return the best-matching overload
     * @throws IllegalStateException if there are no overloads
     */
    public Overload callbackFor(int positionalCount, Set<String> namedNames) {
        Overload fuzzyMatch = null;
        Integer minMismatchDistance = null;

        for (var overload : overloads) {
            // Ideally, find an exact match.
            if (matches(overload.parameters(), positionalCount, namedNames)) {
                return overload;
            }

            int mismatchDistance = overload.parameters().getArguments().size() - positionalCount;

            if (minMismatchDistance != null) {
                if (Math.abs(mismatchDistance) > Math.abs(minMismatchDistance)) continue;
                // If two overloads have the same mismatch distance, favor the overload
                // that has more parameters.
                if (Math.abs(mismatchDistance) == Math.abs(minMismatchDistance)
                        && mismatchDistance < 0) {
                    continue;
                }
            }

            minMismatchDistance = mismatchDistance;
            fuzzyMatch = overload;
        }

        if (fuzzyMatch != null) return fuzzyMatch;
        throw new IllegalStateException("BuiltInCallable " + name + " may not have empty overloads.");
    }

    /**
     * Returns whether {@code positionalCount} and {@code namedNames} are valid
     * for the given parameter declaration.
     *
     * <p>This mirrors the {@code matches} method from Dart's ParameterList.
     */
    private static boolean matches(ArgumentDeclaration declaration,
                                    int positionalCount, Set<String> namedNames) {
        var params = declaration.getArguments();
        int namedUsed = 0;

        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            if (i < positionalCount) {
                if (namedNames.contains(param.getName())) return false;
            } else if (namedNames.contains(param.getName())) {
                namedUsed++;
            } else if (param.getDefaultValue() == null) {
                return false;
            }
        }

        if (declaration.getRestArgument() != null) return true;
        if (positionalCount > params.size()) return false;
        if (namedUsed < namedNames.size()) return false;
        return true;
    }

    /** Returns a copy of this callable with the given {@code name}. */
    public BuiltInCallable withName(String name) {
        return new BuiltInCallable(name, overloads, acceptsContent);
    }

    @Override
    public String getName() {
        return name;
    }

    /** Returns the list of overloads declared for this callable. */
    public List<Overload> getOverloads() {
        return overloads;
    }

    /** Returns whether this callable accepts a content block. */
    public boolean getAcceptsContent() {
        return acceptsContent;
    }

    // -----------------------------------------------------------------------
    // Parameter string parsing
    // -----------------------------------------------------------------------

    /**
     * Parses a parameter declaration string like {@code "$x, $y: 0, $args..."}
     * into an {@link ArgumentDeclaration}.
     *
     * <p>This is a simplified parser for built-in function parameter strings.
     * It handles:
     * <ul>
     *     <li>Simple parameters: {@code $name}</li>
     *     <li>Parameters with defaults: {@code $name: defaultExpr}</li>
     *     <li>Rest parameters: {@code $args...}</li>
     *     <li>Empty parameter lists (empty string)</li>
     * </ul>
     *
     * @param functionName the name of the function (used for span generation)
     * @param parameters the parameter string to parse
     * @return a parsed ArgumentDeclaration
     */
    private static ArgumentDeclaration parseParameters(String functionName, String parameters) {
        // Create a synthetic source for span generation
        var sourceText = "@function " + functionName + "(" + parameters + ") {";
        var sourceFile = new SourceFile(sourceText);
        var fullSpan = sourceFile.span(0, sourceText.length());

        var trimmed = parameters.trim();
        if (trimmed.isEmpty()) {
            return new ArgumentDeclaration(List.of(), null, fullSpan);
        }

        var parts = splitParameterString(trimmed);
        var arguments = new ArrayList<Argument>();
        @Nullable String restArgument = null;

        for (var part : parts) {
            var param = part.trim();
            if (param.isEmpty()) continue;

            // Check for rest parameter: $args...
            if (param.endsWith("...")) {
                var restName = param.substring(0, param.length() - 3).trim();
                if (restName.startsWith("$")) {
                    restName = restName.substring(1);
                }
                restArgument = restName;
                continue;
            }

            // Check for default value: $name: defaultExpr
            int colonIndex = findDefaultSeparator(param);
            if (colonIndex >= 0) {
                var namePart = param.substring(0, colonIndex).trim();
                if (namePart.startsWith("$")) {
                    namePart = namePart.substring(1);
                }
                // The default value expression is stored as a string for now;
                // BuiltInCallable overloads are matched by structure, not by
                // evaluating defaults. We create a dummy expression for the
                // default value so that ArgumentDeclaration knows a default exists.
                var defaultStr = param.substring(colonIndex + 1).trim();
                arguments.add(new Argument(
                        namePart,
                        createDummyExpression(defaultStr, fullSpan),
                        fullSpan
                ));
            } else {
                // Simple parameter: $name
                var namePart = param.trim();
                if (namePart.startsWith("$")) {
                    namePart = namePart.substring(1);
                }
                arguments.add(new Argument(namePart, null, fullSpan));
            }
        }

        return new ArgumentDeclaration(arguments, restArgument, fullSpan);
    }

    /**
     * Splits a parameter string on commas, respecting parentheses nesting.
     *
     * <p>This handles cases like {@code "$x: rgb(0, 0, 0), $y"} where commas
     * inside parentheses should not be treated as separators.
     */
    private static List<String> splitParameterString(String params) {
        var parts = new ArrayList<String>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            switch (c) {
                case '(' -> depth++;
                case ')' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        parts.add(params.substring(start, i));
                        start = i + 1;
                    }
                }
            }
        }

        parts.add(params.substring(start));
        return parts;
    }

    /**
     * Finds the index of the colon separating a parameter name from its default
     * value, or -1 if there is no default.
     *
     * <p>Only considers the first colon that appears after the parameter name
     * (after the {@code $name} part).
     */
    private static int findDefaultSeparator(String param) {
        // Skip past the $name part to find the colon
        int i = 0;
        if (param.startsWith("$")) i = 1;

        // Skip the name characters
        while (i < param.length() && (Character.isLetterOrDigit(param.charAt(i))
                || param.charAt(i) == '-' || param.charAt(i) == '_')) {
            i++;
        }

        // Skip whitespace
        while (i < param.length() && Character.isWhitespace(param.charAt(i))) {
            i++;
        }

        // Check for colon
        if (i < param.length() && param.charAt(i) == ':') {
            return i;
        }
        return -1;
    }

    /**
     * Creates a dummy expression to represent a default value in a built-in
     * callable's parameter declaration.
     *
     * <p>The actual default value expression text is not needed for built-in
     * callable matching -- only the presence of a default matters (for overload
     * resolution). We use a {@link com.sass.ast.sass.ValueExpression} wrapping
     * {@link SassNull#INSTANCE} as a placeholder.
     */
    private static com.sass.ast.sass.Expression createDummyExpression(
            String expressionText, FileSpan span) {
        return new com.sass.ast.sass.ValueExpression(SassNull.INSTANCE, span);
    }
}
