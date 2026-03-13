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
    }

    private void addBuiltIn(String name, String params,
                             java.util.function.Function<List<Value>, Value> callback) {
        builtInFunctions.put(name, BuiltInCallable.function(name, params, callback));
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
        // Handle & (parent selector reference) or prepend parent selector.
        if (styleRule != null) {
            var parentSelector = styleRule.getSelector().getValue();
            if (selectorText.contains("&")) {
                selectorText = selectorText.replace("&", parentSelector);
            } else {
                selectorText = parentSelector + " " + selectorText;
            }
        }

        var cssSelector = new CssValue<>(selectorText, node.getSelector().getSpan());
        var rule = new ModifiableCssStyleRule(cssSelector, node.getSpan());

        // Nested rules get added to the stylesheet root, not to the parent rule
        var targetParent = styleRule != null ? root : parent;
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
        if (!rule.getChildren().isEmpty()) {
            targetParent.addChild(rule);
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
        // Simple implementation: create the media rule with the query text as a single condition
        var queries = List.of(new com.sass.ast.css.CssMediaQuery(List.of(queryText)));
        var rule = new ModifiableCssMediaRule(queries, node.getSpan());
        withParent(rule, () -> {
            for (var child : node.getChildren()) {
                child.accept(this);
            }
        });
        return null;
    }

    @Override
    public @Nullable Value visitSupportsRule(SupportsRule node) {
        var conditionText = visitSupportsCondition(node.getCondition());
        var condition = new CssValue<>(conditionText, node.getSpan());
        var rule = new ModifiableCssSupportsRule(condition, node.getSpan());
        withParent(rule, () -> {
            for (var child : node.getChildren()) {
                child.accept(this);
            }
        });
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
        } else {
            var rule = new ModifiableCssAtRule(cssName, cssValue, false, node.getSpan());
            withParent(rule, () -> {
                for (var child : node.getChildren()) {
                    child.accept(this);
                }
            });
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
        if (importer == null) {
            throw new SassRuntimeException(
                    "Can't find stylesheet to import.", node.getSpan());
        }

        var url = node.getUrl();

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
        if (importer == null) {
            throw new SassRuntimeException(
                    "Can't find stylesheet to import.", node.getSpan());
        }

        var module = loadModule(node.getUrl(), node.getSpan(), List.of(), null);
        if (module != null) {
            // Create a filtered view of the module
            var forwarded = module.forward(
                    node.getShownVariables(),
                    node.getShownMixinsAndFunctions(),
                    node.getHiddenVariables(),
                    node.getHiddenMixinsAndFunctions(),
                    node.getPrefix());
            environment.forwardModule(forwarded);
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
            var oldStyleRule = styleRule;
            var oldSourceUri = currentSourceUri;
            parent = moduleRoot;
            root = moduleRoot;
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
     */
    private void inlineModuleCss(Module module) {
        for (var child : module.getCss().getChildren()) {
            root.addChild(child);
        }
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
        // First check user-defined functions (with namespace support)
        var callable = environment.getFunction(node.getName(), node.getNamespace());
        if (callable instanceof UserDefinedCallable udc) {
            return runUserDefinedFunction(node.getArguments(), udc, node.getSpan());
        }

        // If a namespace was specified and we didn't find it, that's an error
        if (node.getNamespace() != null) {
            throw new SassRuntimeException(
                    "Undefined function \"" + node.getNamespace() + "." + node.getName() + "\".",
                    node.getSpan());
        }

        // Then check built-in functions
        var builtIn = builtInFunctions.get(node.getName());
        if (builtIn != null) {
            return runBuiltInCallable(node.getArguments(), builtIn);
        }

        // Fall back to plain CSS function call
        return visitPlainCssFunction(node);
    }

    @Override
    public Value visitInterpolatedFunctionExpression(InterpolatedFunctionExpression node) {
        var name = performInterpolation(node.getName());
        // Treat as a plain CSS function
        var argStrings = new ArrayList<String>();
        for (var arg : node.getArguments().getPositional()) {
            argStrings.add(toCss(arg.accept(this)));
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

    /** Converts a value to its CSS string representation. */
    private String toCss(Value value) {
        if (value instanceof SassNull) return "";
        return CssSerializer.serializeValue(value, false, false);
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
            argStrings.add(toCss(value));
        }
        for (var entry : node.getArguments().getNamed().entrySet()) {
            var value = entry.getValue().accept(this);
            argStrings.add(toCss(value));
        }
        return new SassString(
                node.getOriginalName() + "(" + String.join(", ", argStrings) + ")",
                false);
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
