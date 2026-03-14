package com.sass.parse;

import com.sass.ast.sass.*;
import com.sass.ast.sass.IfRule.ElseClause;
import com.sass.ast.sass.IfRule.IfClause;
import com.sass.ast.sass.MapExpression.ExpressionPair;
import com.sass.exception.SassFormatException;
import com.sass.util.Characters;
import com.sass.util.FileSpan;
import com.sass.value.ListSeparator;
import com.sass.value.SassColor;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.sass.parse.CharCodes.*;

/**
 * The base class for both the SCSS and indented syntax parsers.
 *
 * <p>Having a base class that's separate from both parsers allows us to make
 * explicit exactly which methods are different between the two. This allows
 * the author to know that if they're modifying the base class, the subclasses
 * generally won't need modification. Conversely, if they're modifying one
 * subclass, the other will likely need a parallel change.
 *
 * <p>Port of {@code lib/src/parse/stylesheet.dart}.
 */
public abstract class StylesheetParser extends Parser {

    // -- State fields --

    /** Whether we've consumed a rule other than @charset, @forward, or @use. */
    private boolean isUseAllowed = true;

    /** Whether the parser is currently parsing the contents of a mixin declaration. */
    private boolean inMixin = false;

    /** Whether the parser is currently parsing a content block passed to a mixin. */
    private boolean inContentBlock = false;

    /** Whether the parser is currently parsing a control directive. */
    private boolean inControlDirective = false;

    /** Whether the parser is currently parsing an unknown rule. */
    private boolean inUnknownAtRule = false;

    /** Whether the parser is currently parsing a style rule. */
    private boolean inStyleRule = false;

    /** Whether the parser is currently within a parenthesized expression. */
    private boolean inParentheses = false;

    /** Whether the parser is currently within an expression. */
    protected boolean inExpression = false;

    /** The silent comment this parser encountered previously. */
    protected @Nullable SilentComment lastSilentComment;

    // -- Known color names (partial set for hex-color parsing) --
    private static final Map<String, SassColor> COLOR_NAMES_BY_NAME = initColorNames();

    private static Map<String, SassColor> initColorNames() {
        // A representative subset of CSS named colors for identifier-like parsing.
        // The full map would be populated from a color_names.dart port.
        var map = new HashMap<String, SassColor>();
        map.put("red", SassColor.rgb(255, 0, 0));
        map.put("green", SassColor.rgb(0, 128, 0));
        map.put("blue", SassColor.rgb(0, 0, 255));
        map.put("white", SassColor.rgb(255, 255, 255));
        map.put("black", SassColor.rgb(0, 0, 0));
        map.put("yellow", SassColor.rgb(255, 255, 0));
        map.put("cyan", SassColor.rgb(0, 255, 255));
        map.put("magenta", SassColor.rgb(255, 0, 255));
        map.put("orange", SassColor.rgb(255, 165, 0));
        map.put("purple", SassColor.rgb(128, 0, 128));
        map.put("pink", SassColor.rgb(255, 192, 203));
        map.put("gray", SassColor.rgb(128, 128, 128));
        map.put("grey", SassColor.rgb(128, 128, 128));
        map.put("transparent", SassColor.rgb(0, 0, 0, 0));
        return Collections.unmodifiableMap(map);
    }

    // -- Constructor --

    protected StylesheetParser(String contents, @Nullable URI url) {
        super(contents, url);
    }

    // =========================================================================
    // Abstract methods (implemented by ScssParser / SassParser)
    // =========================================================================

    /** Whether this is parsing the indented syntax. */
    protected abstract boolean isIndented();

    /** The indentation level at the current scanner position. */
    protected abstract int currentIndentation();

    /** Parses and returns a selector used in a style rule. */
    protected abstract Interpolation styleRuleSelector();

    /**
     * Asserts that the scanner is positioned before a statement separator, or at
     * the end of a list of statements.
     */
    protected abstract void expectStatementSeparator(@Nullable String name);

    /** Whether the scanner is positioned at the end of a statement. */
    protected abstract boolean atEndOfStatement();

    /** Whether the scanner is positioned before a block of children. */
    protected abstract boolean lookingAtChildren();

    /**
     * Tries to scan an {@code @else} rule after an {@code @if} block, and returns whether
     * that succeeded.
     */
    protected abstract boolean scanElse(int ifIndentation);

    /** Consumes a block of child statements. */
    protected abstract List<Statement> children(Supplier<Statement> child);

    /** Consumes top-level statements. */
    protected abstract List<Statement> statements(Supplier<@Nullable Statement> statement);

    // =========================================================================
    // Convenience overloads
    // =========================================================================

    protected void expectStatementSeparator() {
        expectStatementSeparator(null);
    }

    /**
     * Consumes whitespace including comments. For SCSS syntax, newlines are
     * always consumed as whitespace. The indented syntax subclass would
     * override to handle newline sensitivity.
     */
    protected void whitespace() {
        whitespace(true);
    }

    /**
     * Consumes whitespace without comments. For SCSS syntax, newlines are
     * always consumed.
     */
    protected void whitespaceWithoutComments() {
        whitespaceWithoutComments(true);
    }

    // =========================================================================
    // Statement parsing
    // =========================================================================

    /**
     * Main entry point: parses the stylesheet.
     */
    public Stylesheet parse() {
        return wrapSpanFormatException(() -> {
            var start = scanner.getState();
            // Allow a byte-order mark at the beginning of the document.
            scanner.scanChar(0xFEFF);
            var stmts = this.statements(() -> {
                // Handle @charset specially so that atRule always returns non-null.
                if (scanner.scan("@charset")) {
                    whitespace();
                    string();
                    return null;
                }
                return statement(true);
            });
            scanner.expectDone();
            return new Stylesheet(stmts, spanFrom(start), false);
        });
    }

    /**
     * Consumes a statement that's allowed at the top level of the stylesheet or
     * within nested style and at rules.
     */
    protected Statement statement(boolean root) {
        int next = scanner.peekChar();
        return switch (next) {
            case $at -> atRule(() -> statement(false), root);
            case $plus -> {
                if (!isIndented() || !lookingAtIdentifier(1)) {
                    yield styleRule(null, null);
                }
                isUseAllowed = false;
                var start = scanner.getState();
                scanner.readChar();
                yield includeRule(start);
            }
            case $equal -> {
                if (!isIndented()) yield styleRule(null, null);
                isUseAllowed = false;
                var start = scanner.getState();
                scanner.readChar();
                whitespace();
                yield mixinRule(start);
            }
            case $rbrace -> throw scanner.error("unmatched \"}\".", scanner.getPosition(), 1);
            default -> {
                if (inStyleRule || inUnknownAtRule || inMixin || inContentBlock) {
                    yield declarationOrStyleRule();
                } else {
                    yield variableDeclarationOrStyleRule();
                }
            }
        };
    }

    // =========================================================================
    // Variable declarations
    // =========================================================================

    /**
     * Consumes a variable declaration.
     */
    protected VariableDeclaration variableDeclarationWithoutNamespace() {
        return variableDeclarationWithoutNamespace(null, null);
    }

    /**
     * Consumes a variable declaration.
     */
    protected VariableDeclaration variableDeclarationWithoutNamespace(
            @Nullable String namespace, SpanScanner.@Nullable ScannerState start_) {
        lastSilentComment = null;
        var start = start_ != null ? start_ : scanner.getState();

        var name = variableName();
        whitespace();
        scanner.expectChar($colon);
        whitespace();

        var value = expression();

        var guarded = false;
        var global = false;
        while (scanner.scanChar($exclamation)) {
            var flagName = identifier();
            switch (flagName) {
                case "default" -> guarded = true;
                case "global" -> {
                    if (namespace != null) {
                        throw error("!global isn't allowed for variables in other modules.",
                                spanFrom(start));
                    }
                    global = true;
                }
                default -> throw error("Invalid flag name.", spanFrom(start));
            }
            whitespace();
        }

        expectStatementSeparator("variable declaration");
        return new VariableDeclaration(namespace, name, value, guarded, global, spanFrom(start));
    }

    // =========================================================================
    // Style rules
    // =========================================================================

    /**
     * Tries to parse a namespaced VariableDeclaration or falls back to a StyleRule.
     */
    private Statement variableDeclarationOrStyleRule() {
        if (!lookingAtIdentifier()) return styleRule(null, null);

        var start = scanner.getState();
        var identStr = identifier();
        if (scanner.peekChar() == $dot && scanner.peekChar(1) == $dollar) {
            scanner.readChar(); // consume the '.'
            return variableDeclarationWithoutNamespace(identStr, start);
        }

        // Not a variable declaration -- fall back to style rule
        var buffer = new InterpolationBuffer();
        buffer.write(identStr);
        if (lookingAtInterpolatedIdentifierBody()) {
            buffer.addInterpolation(interpolatedIdentifier());
        }
        var interpolation = buffer.buildInterpolation(spanFrom(start));

        // Build a new buffer with the already-parsed text, then finish as style rule
        var ruleBuffer = new InterpolationBuffer();
        ruleBuffer.addInterpolation(interpolation);
        return styleRule(ruleBuffer, start);
    }

    /**
     * Tries to parse a Declaration or falls back to a StyleRule.
     */
    private Statement declarationOrStyleRule() {
        var start = scanner.getState();
        var result = declarationOrBuffer();
        if (result instanceof Statement stmt) return stmt;
        return styleRule((InterpolationBuffer) result, start);
    }

    /**
     * Tries to parse a declaration. Returns a Statement on success, or an
     * InterpolationBuffer if it should be reparsed as a selector.
     */
    private Object declarationOrBuffer() {
        var start = scanner.getState();
        var nameBuffer = new InterpolationBuffer();

        if (lookingAtPotentialPropertyHack()) {
            nameBuffer.writeCharCode(scanner.readChar());
            nameBuffer.write(rawText(this::whitespace));
        }

        if (!lookingAtInterpolatedIdentifier()) return nameBuffer;

        // Try variable declaration first
        if (!lookingAtPotentialPropertyHack() && lookingAtIdentifier()) {
            var idStart = scanner.getState();
            var ident = identifier();
            if (scanner.peekChar() == $dot && scanner.peekChar(1) == $dollar) {
                scanner.readChar(); // consume the '.'
                return variableDeclarationWithoutNamespace(ident, idStart);
            }
            // Not a variable; use as interpolation
            var buf = new InterpolationBuffer();
            buf.write(ident);
            if (lookingAtInterpolatedIdentifierBody()) {
                buf.addInterpolation(interpolatedIdentifier());
            }
            nameBuffer.addInterpolation(buf.buildInterpolation(spanFrom(idStart)));
        } else {
            nameBuffer.addInterpolation(interpolatedIdentifier());
        }

        isUseAllowed = false;
        var midBuffer = new StringBuilder();
        midBuffer.append(rawText(this::whitespace));
        if (!scanner.scanChar($colon)) {
            if (midBuffer.length() > 0) nameBuffer.writeCharCode($space);
            return nameBuffer;
        }
        midBuffer.append(':');

        var name = nameBuffer.buildInterpolation(spanFrom(start));

        // Parse custom properties as declarations no matter what.
        if (name.initialPlain().startsWith("--")) {
            Expression value;
            if (atEndOfStatement()) {
                value = new StringExpression(
                        new Interpolation(List.of(), scanner.emptySpan()));
            } else {
                value = new StringExpression(interpolatedDeclarationValue(false));
            }
            expectStatementSeparator("custom property");
            return new Declaration(name, value, false, null, false, spanFrom(start));
        }

        if (scanner.scanChar($colon)) {
            nameBuffer.write(midBuffer.toString());
            nameBuffer.writeCharCode($colon);
            return nameBuffer;
        }

        var postColonWhitespace = rawText(this::whitespace);

        // Try parsing nested declaration children
        if (lookingAtChildren()) {
            var nestedChildren = children(() -> propertyOrVariableDeclaration());
            return new Declaration(name, null, true, nestedChildren, true, spanFrom(start));
        }

        midBuffer.append(postColonWhitespace);
        var beforeDeclaration = scanner.getState();
        try {
            var value = expression();
            if (lookingAtChildren()) {
                var nestedChildren = children(() -> propertyOrVariableDeclaration());
                return new Declaration(name, value, true, nestedChildren, true, spanFrom(start));
            } else if (!atEndOfStatement()) {
                expectStatementSeparator();
            }
            expectStatementSeparator();
            return new Declaration(name, value, true, null, false, spanFrom(start));
        } catch (SassFormatException e) {
            // If it fails, fall back to selector parsing
            scanner.setState(beforeDeclaration);
            nameBuffer.write(midBuffer.toString());
            nameBuffer.addInterpolation(almostAnyValue());
            return nameBuffer;
        }
    }

    /**
     * Consumes a StyleRule, optionally with a buffer that may contain some
     * text that has already been parsed.
     */
    private StyleRule styleRule(@Nullable InterpolationBuffer buffer,
                                SpanScanner.@Nullable ScannerState start_) {
        isUseAllowed = false;
        var start = start_ != null ? start_ : scanner.getState();

        var interpolation = styleRuleSelector();
        if (buffer != null) {
            buffer.addInterpolation(interpolation);
            interpolation = buffer.buildInterpolation(spanFrom(start));
        }
        if (interpolation.getContents().isEmpty()) {
            throw scanner.error("expected \"}\".");
        }

        var wasInStyleRule = inStyleRule;
        inStyleRule = true;
        var childStatements = children(() -> statement(false));
        inStyleRule = wasInStyleRule;

        return new StyleRule(interpolation, childStatements, false, spanFrom(start));
    }

    /**
     * Consumes either a property declaration or a namespaced variable declaration.
     */
    private Statement propertyOrVariableDeclaration() {
        var start = scanner.getState();
        Interpolation name;

        if (lookingAtPotentialPropertyHack()) {
            var nameBuffer = new InterpolationBuffer();
            nameBuffer.writeCharCode(scanner.readChar());
            nameBuffer.write(rawText(this::whitespace));
            nameBuffer.addInterpolation(interpolatedIdentifier());
            name = nameBuffer.buildInterpolation(spanFrom(start));
        } else if (lookingAtIdentifier()) {
            var idStart = scanner.getState();
            var ident = identifier();
            if (scanner.peekChar() == $dot && scanner.peekChar(1) == $dollar) {
                scanner.readChar();
                return variableDeclarationWithoutNamespace(ident, idStart);
            }
            var buf = new InterpolationBuffer();
            buf.write(ident);
            if (lookingAtInterpolatedIdentifierBody()) {
                buf.addInterpolation(interpolatedIdentifier());
            }
            name = buf.buildInterpolation(spanFrom(idStart));
        } else {
            name = interpolatedIdentifier();
        }

        whitespace();
        scanner.expectChar($colon);
        whitespace();

        if (lookingAtChildren()) {
            var nestedChildren = children(() -> propertyOrVariableDeclaration());
            return new Declaration(name, null, true, nestedChildren, true, spanFrom(start));
        }

        var value = expression();
        if (lookingAtChildren()) {
            var nestedChildren = children(() -> propertyOrVariableDeclaration());
            return new Declaration(name, value, true, nestedChildren, true, spanFrom(start));
        } else {
            expectStatementSeparator();
            return new Declaration(name, value, true, null, false, spanFrom(start));
        }
    }

    // =========================================================================
    // At-rules
    // =========================================================================

    /**
     * Consumes an at-rule. Dispatches to specific at-rule methods based on name.
     */
    protected Statement atRule(Supplier<Statement> child, boolean root) {
        var start = scanner.getState();
        scanner.expectChar($at, "@-rule");
        var name = interpolatedIdentifier();

        var wasUseAllowed = isUseAllowed;
        isUseAllowed = false;

        String plainName = name.asPlain();
        if (plainName != null) {
            return switch (plainName) {
                case "at-root" -> atRootRule(start);
                case "content" -> contentRule(start);
                case "debug" -> debugRule(start);
                case "each" -> eachRule(start, child);
                case "else" -> disallowedAtRule(start);
                case "error" -> errorRule(start);
                case "extend" -> extendRule(start);
                case "for" -> forRule(start, child);
                case "forward" -> {
                    isUseAllowed = wasUseAllowed;
                    if (!root) disallowedAtRule(start);
                    yield forwardRule(start);
                }
                case "function" -> functionRule(start, name);
                case "if" -> ifRule(start, child);
                case "import" -> importRule(start);
                case "include" -> includeRule(start);
                case "media" -> mediaRule(start);
                case "mixin" -> mixinRule(start);
                case "return" -> disallowedAtRule(start);
                case "supports" -> supportsRule(start);
                case "use" -> {
                    isUseAllowed = wasUseAllowed;
                    if (!root) disallowedAtRule(start);
                    yield useRule(start);
                }
                case "warn" -> warnRule(start);
                case "while" -> whileRule(start, child);
                default -> unknownAtRule(start, name);
            };
        }
        return unknownAtRule(start, name);
    }

    // -- Individual at-rule methods --

    private AtRootRule atRootRule(SpanScanner.ScannerState start) {
        whitespace();
        Interpolation query = null;
        if (scanner.peekChar() == $lparen) {
            query = atRootQuery();
        }
        var childStmts = children(() -> statement(false));
        return new AtRootRule(query, childStmts, false, spanFrom(start));
    }

    private Interpolation atRootQuery() {
        var start = scanner.getState();
        var buffer = new InterpolationBuffer();
        scanner.expectChar($lparen);
        buffer.writeCharCode($lparen);
        whitespace();
        addOrInject(buffer, expression());
        if (scanner.scanChar($colon)) {
            whitespace();
            buffer.writeCharCode($colon);
            buffer.writeCharCode($space);
            addOrInject(buffer, expression());
        }
        scanner.expectChar($rparen);
        whitespace();
        buffer.writeCharCode($rparen);
        return buffer.buildInterpolation(spanFrom(start));
    }

    private ContentRule contentRule(SpanScanner.ScannerState start) {
        if (!inMixin) {
            throw error("@content is only allowed within mixin declarations.", spanFrom(start));
        }
        whitespace();
        ArgumentInvocation arguments;
        if (scanner.peekChar() == $lparen) {
            arguments = argumentInvocation(true);
            whitespace();
        } else {
            arguments = new ArgumentInvocation(List.of(), Map.of(), null, null, scanner.emptySpan());
        }
        expectStatementSeparator("@content rule");
        return new ContentRule(arguments, spanFrom(start));
    }

    private DebugRule debugRule(SpanScanner.ScannerState start) {
        whitespace();
        var value = expression();
        expectStatementSeparator("@debug rule");
        return new DebugRule(value, spanFrom(start));
    }

    private EachRule eachRule(SpanScanner.ScannerState start, Supplier<Statement> child) {
        whitespace();
        var wasInControlDirective = inControlDirective;
        inControlDirective = true;

        var variables = new ArrayList<String>();
        variables.add(variableName());
        whitespace();
        while (scanner.scanChar($comma)) {
            whitespace();
            variables.add(variableName());
            whitespace();
        }
        expectIdentifier("in");
        whitespace();

        var list = expression();
        var childStmts = children(child);
        inControlDirective = wasInControlDirective;
        return new EachRule(variables, list, childStmts, false, spanFrom(start));
    }

    private ErrorRule errorRule(SpanScanner.ScannerState start) {
        whitespace();
        var value = expression();
        expectStatementSeparator("@error rule");
        return new ErrorRule(value, spanFrom(start));
    }

    private ExtendRule extendRule(SpanScanner.ScannerState start) {
        whitespace();
        if (!inStyleRule && !inMixin && !inContentBlock) {
            throw error("@extend may only be used within style rules.", spanFrom(start));
        }
        var value = almostAnyValue();
        var optional = scanner.scanChar($exclamation);
        if (optional) {
            expectIdentifier("optional");
            whitespace();
        }
        expectStatementSeparator("@extend rule");
        return new ExtendRule(value, optional, spanFrom(start));
    }

    private ForRule forRule(SpanScanner.ScannerState start, Supplier<Statement> child) {
        whitespace();
        var wasInControlDirective = inControlDirective;
        inControlDirective = true;
        var variable = variableName();
        whitespace();
        expectIdentifier("from");
        whitespace();

        // Parse from expression, stopping at "to" or "through"
        boolean[] exclusive = {false};
        boolean[] found = {false};
        var from = expressionUntil(() -> {
            if (!lookingAtIdentifier()) return false;
            if (scanIdentifier("to")) {
                exclusive[0] = true;
                found[0] = true;
                return true;
            } else if (scanIdentifier("through")) {
                exclusive[0] = false;
                found[0] = true;
                return true;
            }
            return false;
        });
        if (!found[0]) {
            throw scanner.error("Expected \"to\" or \"through\".");
        }
        whitespace();
        var to = expression();
        var childStmts = children(child);
        inControlDirective = wasInControlDirective;
        return new ForRule(variable, from, to, exclusive[0], childStmts, false, spanFrom(start));
    }

    private ForwardRule forwardRule(SpanScanner.ScannerState start) {
        whitespace();
        var url = string();
        whitespace();

        String prefix = null;
        if (scanIdentifier("as")) {
            whitespace();
            prefix = identifier();
            scanner.expectChar($asterisk);
            whitespace();
        }

        Set<String> shownMixinsAndFunctions = null;
        Set<String> shownVariables = null;
        Set<String> hiddenMixinsAndFunctions = null;
        Set<String> hiddenVariables = null;

        if (scanIdentifier("show")) {
            whitespace();
            var members = memberList();
            shownMixinsAndFunctions = members[0];
            shownVariables = members[1];
        } else if (scanIdentifier("hide")) {
            whitespace();
            var members = memberList();
            hiddenMixinsAndFunctions = members[0];
            hiddenVariables = members[1];
        }

        var configuration = configuration(true);
        whitespace();
        expectStatementSeparator("@forward rule");
        var span = spanFrom(start);
        if (!isUseAllowed) {
            throw error("@forward rules must be written before any other rules.", span);
        }

        return new ForwardRule(url,
                shownMixinsAndFunctions, shownVariables,
                hiddenMixinsAndFunctions, hiddenVariables,
                prefix, configuration != null ? configuration : List.of(), span);
    }

    @SuppressWarnings("unchecked")
    private Set<String>[] memberList() {
        var identifiers = new LinkedHashSet<String>();
        var variables = new LinkedHashSet<String>();
        do {
            whitespace();
            if (scanner.peekChar() == $dollar) {
                variables.add(variableName());
            } else {
                identifiers.add(identifier());
            }
            whitespace();
        } while (scanner.scanChar($comma));
        return new Set[]{identifiers, variables};
    }

    private Statement functionRule(SpanScanner.ScannerState start, Interpolation atRuleName) {
        whitespace();
        lastSilentComment = null;
        var name = identifier();
        whitespace();
        var parameters = parameterList();

        if (inMixin || inContentBlock) {
            throw error("Mixins may not contain function declarations.", spanFrom(start));
        } else if (inControlDirective) {
            throw error("Functions may not be declared in control directives.", spanFrom(start));
        }

        whitespace();
        var childStmts = children(this::functionChild);
        return new FunctionRule(name, name, parameters, null, childStmts, false, spanFrom(start));
    }

    private Statement functionChild() {
        if (scanner.peekChar() != $at) {
            return variableDeclarationWithoutNamespace();
        }
        var start = scanner.getState();
        var ruleName = plainAtRuleName();
        return switch (ruleName) {
            case "debug" -> debugRule(start);
            case "each" -> eachRule(start, this::functionChild);
            case "else" -> disallowedAtRule(start);
            case "error" -> errorRule(start);
            case "for" -> forRule(start, this::functionChild);
            case "if" -> ifRule(start, this::functionChild);
            case "return" -> returnRule(start);
            case "warn" -> warnRule(start);
            case "while" -> whileRule(start, this::functionChild);
            default -> disallowedAtRule(start);
        };
    }

    private IfRule ifRule(SpanScanner.ScannerState start, Supplier<Statement> child) {
        whitespace();
        var ifIndentation = currentIndentation();
        var wasInControlDirective = inControlDirective;
        inControlDirective = true;
        var condition = expression();
        var ifChildren = this.children(child);

        var clauses = new ArrayList<IfClause>();
        clauses.add(new IfClause(condition, ifChildren));
        ElseClause lastClause = null;

        while (scanElse(ifIndentation)) {
            whitespace();
            if (scanIdentifier("if")) {
                whitespace();
                clauses.add(new IfClause(expression(), this.children(child)));
            } else {
                lastClause = new ElseClause(this.children(child));
                break;
            }
        }
        inControlDirective = wasInControlDirective;
        return new IfRule(clauses, lastClause, spanFrom(start));
    }

    private ImportRule importRule(SpanScanner.ScannerState start) {
        var imports = new ArrayList<Import>();
        do {
            whitespace();
            imports.add(importArgument());
            whitespace();
        } while (scanner.scanChar($comma));
        expectStatementSeparator("@import rule");
        return new ImportRule(imports, spanFrom(start));
    }

    /**
     * Consumes an argument to an {@code @import} rule.
     */
    protected Import importArgument() {
        var start = scanner.getState();
        var url = string();
        var urlSpan = spanFrom(start);
        whitespace();

        if (isPlainImportUrl(url)) {
            return new StaticImport(
                    new Interpolation(List.of(urlSpan.text()), urlSpan),
                    null, spanFrom(start));
        } else {
            return new DynamicImport(url, urlSpan);
        }
    }

    private boolean isPlainImportUrl(String url) {
        if (url.length() < 5) return false;
        if (url.endsWith(".css")) return true;
        if (url.startsWith("//")) return true;
        if (url.startsWith("http://") || url.startsWith("https://")) return true;
        return false;
    }

    private IncludeRule includeRule(SpanScanner.ScannerState start) {
        whitespace();
        String namespace = null;
        var name = identifier();
        if (scanner.scanChar($dot)) {
            namespace = name;
            name = identifier();
        }
        whitespace();
        ArgumentInvocation arguments;
        if (scanner.peekChar() == $lparen) {
            arguments = argumentInvocation(true);
        } else {
            arguments = new ArgumentInvocation(List.of(), Map.of(), null, null, scanner.emptySpan());
        }
        whitespace();

        ArgumentDeclaration contentParameters = null;
        if (scanIdentifier("using")) {
            whitespace();
            contentParameters = parameterList();
            whitespace();
        }

        ContentBlock content = null;
        if (contentParameters != null || lookingAtChildren()) {
            var contentParams = contentParameters != null
                    ? contentParameters
                    : new ArgumentDeclaration(List.of(), null, scanner.emptySpan());
            var wasInContentBlock = inContentBlock;
            inContentBlock = true;
            var contentChildren = children(() -> statement(false));
            content = new ContentBlock(contentParams, contentChildren, false, spanFrom(start));
            inContentBlock = wasInContentBlock;
        } else {
            expectStatementSeparator();
        }

        return new IncludeRule(namespace, name, name, arguments, content, spanFrom(start));
    }

    private MediaRule mediaRule(SpanScanner.ScannerState start) {
        whitespace();
        var query = mediaQueryList();
        var childStmts = children(() -> statement(false));
        return new MediaRule(query, childStmts, false, spanFrom(start));
    }

    private Interpolation mediaQueryList() {
        var start = scanner.getState();
        var buffer = new InterpolationBuffer();
        while (true) {
            whitespace();
            mediaQuery(buffer);
            whitespace();
            if (!scanner.scanChar($comma)) break;
            buffer.writeCharCode($comma);
            buffer.writeCharCode($space);
        }
        return buffer.buildInterpolation(spanFrom(start));
    }

    private void mediaQuery(InterpolationBuffer buffer) {
        // Matches dart-sass _mediaQuery: handles all valid media query forms.
        if (scanner.peekChar() == $lparen) {
            mediaInParens(buffer);
            whitespace();
            if (scanIdentifier("and")) {
                buffer.write(" and ");
                whitespace();
                mediaLogicSequence(buffer, "and");
            } else if (scanIdentifier("or")) {
                buffer.write(" or ");
                whitespace();
                mediaLogicSequence(buffer, "or");
            }
            return;
        }

        var identifier1 = interpolatedIdentifier();
        String plain1 = identifier1.asPlain();
        if (plain1 != null && plain1.equalsIgnoreCase("not")) {
            // "@media not (...) {"
            whitespace();
            if (!lookingAtInterpolatedIdentifier()) {
                buffer.write("not ");
                mediaOrInterp(buffer);
                return;
            }
        }

        whitespace();
        buffer.addInterpolation(identifier1);
        if (!lookingAtInterpolatedIdentifier()) return;

        buffer.writeCharCode($space);
        var identifier2 = interpolatedIdentifier();
        String plain2 = identifier2.asPlain();
        if (plain2 != null && plain2.equalsIgnoreCase("and")) {
            whitespace();
            buffer.write(" and ");
        } else {
            whitespace();
            buffer.addInterpolation(identifier2);
            if (scanIdentifier("and")) {
                whitespace();
                buffer.write(" and ");
            } else {
                return;
            }
        }

        // We've consumed either `IDENTIFIER "and"` or `IDENTIFIER IDENTIFIER "and"`.
        if (scanIdentifier("not")) {
            whitespace();
            buffer.write("not ");
            mediaOrInterp(buffer);
            return;
        }

        mediaLogicSequence(buffer, "and");
    }

    private void mediaLogicSequence(InterpolationBuffer buffer, String operator) {
        while (true) {
            mediaOrInterp(buffer);
            whitespace();
            if (!scanIdentifier(operator)) return;
            whitespace(); // consume whitespace after "and"/"or" before next condition
            buffer.writeCharCode($space);
            buffer.write(operator);
            buffer.writeCharCode($space);
        }
    }

    private void mediaOrInterp(InterpolationBuffer buffer) {
        if (scanner.peekChar() == $hash && scanner.peekChar(1) == $lbrace) {
            buffer.addInterpolation(interpolatedIdentifier());
        } else {
            mediaInParens(buffer);
        }
    }

    private void mediaInParens(InterpolationBuffer buffer) {
        scanner.expectChar($lparen);
        buffer.writeCharCode($lparen);
        whitespace();
        addOrInject(buffer, expression());
        if (scanner.scanChar($colon)) {
            whitespace();
            buffer.writeCharCode($colon);
            buffer.writeCharCode($space);
            addOrInject(buffer, expression());
        }
        scanner.expectChar($rparen);
        whitespace();
        buffer.writeCharCode($rparen);
    }

    private MixinRule mixinRule(SpanScanner.ScannerState start) {
        whitespace();
        lastSilentComment = null;
        var name = identifier();
        whitespace();
        ArgumentDeclaration parameters;
        if (scanner.peekChar() == $lparen) {
            parameters = parameterList();
        } else {
            parameters = new ArgumentDeclaration(List.of(), null, scanner.emptySpan());
        }

        if (inMixin || inContentBlock) {
            throw error("Mixins may not contain mixin declarations.", spanFrom(start));
        } else if (inControlDirective) {
            throw error("Mixins may not be declared in control directives.", spanFrom(start));
        }

        whitespace();
        inMixin = true;
        var childStmts = children(() -> statement(false));
        inMixin = false;
        return new MixinRule(name, name, parameters, null, false, childStmts, false, spanFrom(start));
    }

    private ReturnRule returnRule(SpanScanner.ScannerState start) {
        whitespace();
        var value = expression();
        expectStatementSeparator("@return rule");
        return new ReturnRule(value, spanFrom(start));
    }

    private SupportsRule supportsRule(SpanScanner.ScannerState start) {
        whitespace();
        var condition = supportsCondition();
        whitespace();
        var childStmts = children(() -> statement(false));
        return new SupportsRule(condition, childStmts, false, spanFrom(start));
    }

    /**
     * Consumes a {@code @supports} condition.
     *
     * <p>If {@code inParentheses} is true, the indented syntax will consume
     * newlines where a statement otherwise would end.
     *
     * <p>Port of {@code _supportsCondition} in stylesheet.dart.
     */
    private SupportsCondition supportsCondition() {
        return supportsCondition(false);
    }

    private SupportsCondition supportsCondition(boolean inParentheses) {
        var start = scanner.getState();
        if (scanIdentifier("not")) {
            whitespace();
            return new SupportsNegation(
                    supportsConditionInParens(), spanFrom(start));
        }

        var condition = supportsConditionInParens();
        whitespace();
        BooleanOperator operator = null;
        while (lookingAtIdentifier()) {
            if (operator != null) {
                expectIdentifier(operator.getCssName());
            } else if (scanIdentifier("or")) {
                operator = BooleanOperator.OR;
            } else {
                expectIdentifier("and");
                operator = BooleanOperator.AND;
            }

            whitespace();
            var right = supportsConditionInParens();
            condition = new SupportsOperation(
                    condition, right, operator, spanFrom(start));
            whitespace();
        }
        return condition;
    }

    /**
     * Consumes a parenthesized supports condition, or an interpolation.
     *
     * <p>Port of {@code _supportsConditionInParens} in stylesheet.dart.
     */
    private SupportsCondition supportsConditionInParens() {
        var start = scanner.getState();

        if (lookingAtInterpolatedIdentifier()) {
            var identifier = interpolatedIdentifier();
            String plain = identifier.asPlain();
            if (plain != null && plain.toLowerCase(Locale.ROOT).equals("not")) {
                throw error("\"not\" is not a valid identifier here.", identifier.getSpan());
            }

            if (scanner.scanChar($lparen)) {
                var arguments = interpolatedDeclarationValue(
                        true, true, true, true, true);
                scanner.expectChar($rparen);
                return new SupportsFunction(identifier, arguments, spanFrom(start));
            } else if (identifier.getContents().size() == 1
                       && identifier.getContents().get(0) instanceof Expression expr) {
                return new SupportsInterpolation(expr, spanFrom(start));
            } else {
                throw error("Expected @supports condition.", identifier.getSpan());
            }
        }

        scanner.expectChar($lparen);
        whitespace();
        if (scanIdentifier("not")) {
            whitespace();
            var condition = supportsConditionInParens();
            scanner.expectChar($rparen);
            return new SupportsNegation(condition, spanFrom(start));
        } else if (scanner.peekChar() == $lparen) {
            var condition = supportsCondition(true);
            scanner.expectChar($rparen);
            return condition.withSpan(spanFrom(start));
        }

        // Try to parse as a declaration: Expression ":" Expression
        // If that fails, try as an identifier-based anything value.
        Expression name;
        var nameStart = scanner.getState();
        var wasInParentheses = inParentheses;
        try {
            name = expression();
            scanner.expectChar($colon);
        } catch (SassFormatException e) {
            scanner.setState(nameStart);
            inParentheses = wasInParentheses;

            var identifier = interpolatedIdentifier();
            var operation = trySupportsOperation(identifier, nameStart);
            if (operation != null) {
                scanner.expectChar($rparen);
                return operation.withSpan(spanFrom(start));
            }

            // If parsing an expression fails, try to parse an
            // InterpolatedAnyValue instead. But if that value runs into a
            // top-level colon, then this is probably intended to be a declaration
            // after all, so we rethrow the declaration-parsing error.
            var contentsBuffer = new InterpolationBuffer();
            contentsBuffer.addInterpolation(identifier);
            contentsBuffer.addInterpolation(
                    interpolatedDeclarationValue(true, true, false, true, true));
            var contents = contentsBuffer.buildInterpolation(spanFrom(nameStart));
            if (scanner.peekChar() == $colon) throw e;

            scanner.expectChar($rparen);
            return new SupportsAnything(contents, spanFrom(start));
        }

        var value = supportsDeclarationValue(name);
        scanner.expectChar($rparen);
        return new SupportsDeclaration(name, value, spanFrom(start));
    }

    /**
     * Parses and returns the right-hand side of a declaration in a supports query.
     */
    private Expression supportsDeclarationValue(Expression name) {
        if (name instanceof StringExpression strExpr
                && !strExpr.hasQuotes()
                && strExpr.getText().initialPlain().startsWith("--")) {
            return new StringExpression(interpolatedDeclarationValue(false));
        } else {
            whitespace();
            return expression();
        }
    }

    /**
     * If {@code interpolation} is followed by "and" or "or", parse it as a
     * supports operation. Otherwise, return null without moving the scanner position.
     *
     * <p>Port of {@code _trySupportsOperation} in stylesheet.dart.
     */
    private @Nullable SupportsOperation trySupportsOperation(
            Interpolation interpolation, SpanScanner.ScannerState start) {
        if (interpolation.getContents().size() != 1) return null;
        var first = interpolation.getContents().get(0);
        if (!(first instanceof Expression expression)) return null;

        var beforeWhitespace = scanner.getState();
        whitespace();

        SupportsOperation operation = null;
        BooleanOperator operator = null;
        while (lookingAtIdentifier()) {
            if (operator != null) {
                expectIdentifier(operator.getCssName());
            } else if (scanIdentifier("and")) {
                operator = BooleanOperator.AND;
            } else if (scanIdentifier("or")) {
                operator = BooleanOperator.OR;
            } else {
                scanner.setState(beforeWhitespace);
                return null;
            }

            whitespace();
            var right = supportsConditionInParens();
            operation = new SupportsOperation(
                    operation != null ? operation
                            : new SupportsInterpolation(expression, interpolation.getSpan()),
                    right, operator, spanFrom(start));
            whitespace();
        }

        return operation;
    }

    private UseRule useRule(SpanScanner.ScannerState start) {
        whitespace();
        var url = string();
        whitespace();

        String namespace = null;
        if (scanIdentifier("as")) {
            whitespace();
            if (scanner.scanChar($asterisk)) {
                namespace = null;
            } else {
                namespace = identifier();
            }
        } else {
            // Default namespace from URL
            // For built-in modules (sass:xxx), the namespace is the module name
            if (url.startsWith("sass:")) {
                namespace = url.substring(5);
            } else {
                int lastSlash = url.lastIndexOf('/');
                String basename = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
                int dot = basename.indexOf('.');
                namespace = basename.substring(
                        basename.startsWith("_") ? 1 : 0,
                        dot == -1 ? basename.length() : dot);
            }
        }

        whitespace();
        var config = configuration(false);
        whitespace();
        var span = spanFrom(start);
        if (!isUseAllowed) {
            throw error("@use rules must be written before any other rules.", span);
        }
        expectStatementSeparator("@use rule");
        return new UseRule(url, namespace, config != null ? config : List.of(), span);
    }

    private WarnRule warnRule(SpanScanner.ScannerState start) {
        whitespace();
        var value = expression();
        expectStatementSeparator("@warn rule");
        return new WarnRule(value, spanFrom(start));
    }

    private WhileRule whileRule(SpanScanner.ScannerState start, Supplier<Statement> child) {
        whitespace();
        var wasInControlDirective = inControlDirective;
        inControlDirective = true;
        var condition = expression();
        var childStmts = children(child);
        inControlDirective = wasInControlDirective;
        return new WhileRule(condition, childStmts, false, spanFrom(start));
    }

    /**
     * Consumes an at-rule that's not explicitly supported by Sass.
     */
    protected AtRule unknownAtRule(SpanScanner.ScannerState start, Interpolation name) {
        var wasInUnknownAtRule = inUnknownAtRule;
        inUnknownAtRule = true;
        whitespace();

        Interpolation value = null;
        if (scanner.peekChar() != $exclamation && !atEndOfStatement()) {
            // Use allowOpenBrace=false so that '{' stops value parsing and is
            // recognized as the start of children (matching dart-sass's almostAnyValue).
            value = interpolatedDeclarationValue(false, false, true, false, false);
        }

        try {
            if (lookingAtChildren()) {
                var childStmts = children(() -> statement(false));
                return new AtRule(name, value, false, childStmts, false, spanFrom(start));
            } else {
                expectStatementSeparator();
                return new AtRule(name, value, true, null, false, spanFrom(start));
            }
        } finally {
            inUnknownAtRule = wasInUnknownAtRule;
        }
    }

    private Statement disallowedAtRule(SpanScanner.ScannerState start) {
        whitespace();
        // Consume remaining value
        interpolatedDeclarationValueAllowEmpty();
        throw error("This at-rule is not allowed here.", spanFrom(start));
    }

    private String plainAtRuleName() {
        scanner.expectChar($at, "@-rule");
        var name = identifier();
        whitespace();
        return name;
    }

    // =========================================================================
    // Parameter and argument lists
    // =========================================================================

    /**
     * Consumes a parameter list for a function or mixin declaration.
     */
    protected ArgumentDeclaration parameterList() {
        var start = scanner.getState();
        scanner.expectChar($lparen);
        whitespace();
        var parameters = new ArrayList<Argument>();
        var named = new HashSet<String>();
        String restParameter = null;
        while (scanner.peekChar() == $dollar) {
            var variableStart = scanner.getState();
            var name = variableName();
            whitespace();

            Expression defaultValue = null;
            if (scanner.scanChar($colon)) {
                whitespace();
                defaultValue = expressionUntilComma();
            } else if (scanner.scanChar($dot)) {
                scanner.expectChar($dot);
                scanner.expectChar($dot);
                whitespace();
                if (scanner.scanChar($comma)) whitespace();
                restParameter = name;
                break;
            }

            parameters.add(new Argument(name, defaultValue, spanFrom(variableStart)));
            if (!named.add(name)) {
                throw error("Duplicate parameter.", parameters.get(parameters.size() - 1).getSpan());
            }
            if (!scanner.scanChar($comma)) break;
            whitespace();
        }
        scanner.expectChar($rparen);
        return new ArgumentDeclaration(parameters, restParameter, spanFrom(start));
    }

    /**
     * Consumes an argument invocation.
     */
    protected ArgumentInvocation argumentInvocation(boolean mixin) {
        var start = scanner.getState();
        scanner.expectChar($lparen);
        whitespace();

        var positional = new ArrayList<Expression>();
        var named = new LinkedHashMap<String, Expression>();
        Expression rest = null;
        Expression keywordRest = null;

        while (lookingAtExpression()) {
            var expr = expressionUntilComma(!mixin);
            whitespace();

            if (expr instanceof VariableExpression varExpr && scanner.scanChar($colon)) {
                whitespace();
                named.put(varExpr.getName(), expressionUntilComma(!mixin));
            } else if (scanner.scanChar($dot)) {
                scanner.expectChar($dot);
                scanner.expectChar($dot);
                if (rest == null) {
                    rest = expr;
                } else {
                    keywordRest = expr;
                    whitespace();
                    if (scanner.scanChar($comma)) whitespace();
                    break;
                }
            } else {
                positional.add(expr);
            }
            whitespace();
            if (!scanner.scanChar($comma)) break;
            whitespace();
        }
        scanner.expectChar($rparen);
        return new ArgumentInvocation(positional, named, rest, keywordRest, spanFrom(start));
    }

    /**
     * Returns the list of configured variables from a @use or @forward rule's
     * {@code with} clause, or null if there is none.
     */
    private @Nullable List<ConfiguredVariable> configuration(boolean allowGuarded) {
        if (!scanIdentifier("with")) return null;

        var variableNames = new HashSet<String>();
        var config = new ArrayList<ConfiguredVariable>();
        whitespace();
        scanner.expectChar($lparen);

        while (true) {
            whitespace();
            var variableStart = scanner.getState();
            var name = variableName();
            whitespace();
            scanner.expectChar($colon);
            whitespace();

            var expr = expressionUntilComma();
            var guarded = false;
            if (allowGuarded && scanner.scanChar($exclamation)) {
                if ("default".equals(identifier())) {
                    guarded = true;
                    whitespace();
                } else {
                    throw error("Invalid flag name.", spanFrom(variableStart));
                }
            }

            var span = spanFrom(variableStart);
            if (variableNames.contains(name)) {
                throw error("The same variable may only be configured once.", span);
            }
            variableNames.add(name);
            config.add(new ConfiguredVariable(name, expr, guarded, span));

            if (!scanner.scanChar($comma)) break;
            whitespace();
            if (!lookingAtExpression()) break;
        }
        scanner.expectChar($rparen);
        return config;
    }

    // =========================================================================
    // Expression parsing
    // =========================================================================

    /**
     * Consumes an expression using operator-precedence climbing.
     */
    protected Expression expression() {
        return expressionInternal(false, false, null);
    }

    /**
     * Consumes an expression with an optional stopping condition.
     */
    protected Expression expressionUntil(BooleanSupplier until) {
        return expressionInternal(false, false, until);
    }

    /**
     * Consumes an expression until a top-level comma.
     */
    protected Expression expressionUntilComma() {
        return expressionUntilComma(false);
    }

    protected Expression expressionUntilComma(boolean singleEquals) {
        return expressionInternal(singleEquals, false, () -> scanner.peekChar() == $comma);
    }

    /**
     * The main expression parser. Handles operator precedence climbing,
     * space-separated lists, and comma-separated lists.
     */
    private Expression expressionInternal(boolean singleEquals, boolean bracketList,
                                           @Nullable BooleanSupplier until) {
        if (until != null && until.getAsBoolean()) {
            throw scanner.error("Expected expression.");
        }

        SpanScanner.ScannerState beforeBracket = null;
        if (bracketList) {
            beforeBracket = scanner.getState();
            scanner.expectChar($lbracket);
            whitespace();
            if (scanner.scanChar($rbracket)) {
                return new ListExpression(List.of(), ListSeparator.UNDECIDED, true,
                        spanFrom(beforeBracket));
            }
        }

        var start = scanner.getState();
        var wasInExpression = inExpression;
        var wasInParentheses = inParentheses;
        inExpression = true;

        List<Expression> commaExpressions = null;
        List<Expression> spaceExpressions = null;
        List<BinaryOperator> operators = null;
        List<Expression> operands = null;
        boolean allowSlash = true;

        Expression singleExpression = singleExpression_();

        loop:
        while (true) {
            whitespace();
            if (until != null && until.getAsBoolean()) break;

            int next = scanner.peekChar();
            switch (next) {
                case -1:
                    break loop;

                case $lparen:
                    singleExpression = addSingleExpression(singleExpression, spaceExpressions,
                            operators, operands, allowSlash, parentheses(), wasInParentheses);
                    if (singleExpression == null) {
                        // Reset state needed
                        spaceExpressions = null;
                        operators = null;
                        operands = null;
                        allowSlash = true;
                        scanner.setState(start);
                        singleExpression = singleExpression_();
                        continue;
                    }
                    spaceExpressions = extractSpaceExpressions(singleExpression, spaceExpressions);
                    singleExpression = extractSingleAfterSpace(singleExpression, spaceExpressions);
                    break;

                case $lbracket:
                    var bracketExpr = expressionInternal(false, true, null);
                    if (singleExpression != null) {
                        if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                        spaceExpressions.add(singleExpression);
                        allowSlash = true;
                    }
                    singleExpression = bracketExpr;
                    break;

                case $dollar:
                    if (singleExpression != null) {
                        if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                        spaceExpressions.add(singleExpression);
                        allowSlash = true;
                    }
                    singleExpression = variable();
                    break;

                case $ampersand:
                    if (singleExpression != null) {
                        if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                        spaceExpressions.add(singleExpression);
                        allowSlash = true;
                    }
                    singleExpression = selectorExpression();
                    break;

                case $singleQuote, $doubleQuote:
                    if (singleExpression != null) {
                        if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                        spaceExpressions.add(singleExpression);
                        allowSlash = true;
                    }
                    singleExpression = interpolatedString();
                    break;

                case $hash:
                    if (singleExpression != null) {
                        if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                        spaceExpressions.add(singleExpression);
                        allowSlash = true;
                    }
                    singleExpression = hashExpression();
                    break;

                case $equal: {
                    scanner.readChar();
                    if (singleEquals && scanner.peekChar() != $equal) {
                        // addOperator for single equals
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        operators.add(BinaryOperator.SINGLE_EQUALS);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    } else {
                        scanner.expectChar($equal);
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.EQUALS.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.EQUALS);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    }
                    break;
                }

                case $exclamation: {
                    int next2 = scanner.peekChar(1);
                    if (next2 == $equal) {
                        scanner.readChar();
                        scanner.readChar();
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.NOT_EQUALS.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.NOT_EQUALS);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    } else if (next2 == -1 || next2 == $i || next2 == $I
                               || Characters.isWhitespace(next2)) {
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = importantExpression();
                    } else {
                        break loop;
                    }
                    break;
                }

                case $lt: {
                    scanner.readChar();
                    var op = scanner.scanChar($equal)
                            ? BinaryOperator.LESS_THAN_OR_EQUALS
                            : BinaryOperator.LESS_THAN;
                    allowSlash = false;
                    if (operators == null) operators = new ArrayList<>();
                    if (operands == null) operands = new ArrayList<>();
                    while (!operators.isEmpty()
                            && operators.get(operators.size() - 1).getPrecedence()
                               >= op.getPrecedence()) {
                        singleExpression = resolveOneOperation(operators, operands, singleExpression);
                    }
                    operators.add(op);
                    operands.add(singleExpression);
                    whitespace();
                    singleExpression = singleExpression_();
                    break;
                }

                case $gt: {
                    scanner.readChar();
                    var op = scanner.scanChar($equal)
                            ? BinaryOperator.GREATER_THAN_OR_EQUALS
                            : BinaryOperator.GREATER_THAN;
                    allowSlash = false;
                    if (operators == null) operators = new ArrayList<>();
                    if (operands == null) operands = new ArrayList<>();
                    while (!operators.isEmpty()
                            && operators.get(operators.size() - 1).getPrecedence()
                               >= op.getPrecedence()) {
                        singleExpression = resolveOneOperation(operators, operands, singleExpression);
                    }
                    operators.add(op);
                    operands.add(singleExpression);
                    whitespace();
                    singleExpression = singleExpression_();
                    break;
                }

                case $asterisk: {
                    scanner.readChar();
                    allowSlash = false;
                    if (operators == null) operators = new ArrayList<>();
                    if (operands == null) operands = new ArrayList<>();
                    while (!operators.isEmpty()
                            && operators.get(operators.size() - 1).getPrecedence()
                               >= BinaryOperator.TIMES.getPrecedence()) {
                        singleExpression = resolveOneOperation(operators, operands, singleExpression);
                    }
                    operators.add(BinaryOperator.TIMES);
                    operands.add(singleExpression);
                    whitespace();
                    singleExpression = singleExpression_();
                    break;
                }

                case $plus: {
                    if (singleExpression == null) {
                        singleExpression = unaryOperation();
                    } else {
                        scanner.readChar();
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.PLUS.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.PLUS);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    }
                    break;
                }

                case $minus: {
                    int next1 = scanner.peekChar(1);
                    if ((Characters.isDigit(next1) || next1 == $dot)
                            && (singleExpression == null
                                || (scanner.peekChar(-1) != -1
                                    && Characters.isWhitespace(scanner.peekChar(-1))))) {
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = number();
                    } else if (lookingAtInterpolatedIdentifier()) {
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = identifierLike();
                    } else if (singleExpression == null) {
                        singleExpression = unaryOperation();
                    } else {
                        scanner.readChar();
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.MINUS.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.MINUS);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    }
                    break;
                }

                case $slash: {
                    if (singleExpression == null) {
                        singleExpression = unaryOperation();
                    } else {
                        scanner.readChar();
                        allowSlash = allowSlash; // slash preserves allowSlash
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.DIVIDED_BY.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.DIVIDED_BY);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    }
                    break;
                }

                case $percent: {
                    scanner.readChar();
                    allowSlash = false;
                    if (operators == null) operators = new ArrayList<>();
                    if (operands == null) operands = new ArrayList<>();
                    while (!operators.isEmpty()
                            && operators.get(operators.size() - 1).getPrecedence()
                               >= BinaryOperator.MODULO.getPrecedence()) {
                        singleExpression = resolveOneOperation(operators, operands, singleExpression);
                    }
                    operators.add(BinaryOperator.MODULO);
                    operands.add(singleExpression);
                    whitespace();
                    singleExpression = singleExpression_();
                    break;
                }

                default: {
                    if (next >= $0 && next <= $9) {
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = number();
                    } else if (next == $dot) {
                        if (scanner.peekChar(1) == $dot) break loop;
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = number();
                    } else if (next == $a && scanIdentifier("and")) {
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.AND.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.AND);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    } else if (next == $o && scanIdentifier("or")) {
                        allowSlash = false;
                        if (operators == null) operators = new ArrayList<>();
                        if (operands == null) operands = new ArrayList<>();
                        while (!operators.isEmpty()
                                && operators.get(operators.size() - 1).getPrecedence()
                                   >= BinaryOperator.OR.getPrecedence()) {
                            singleExpression = resolveOneOperation(operators, operands, singleExpression);
                        }
                        operators.add(BinaryOperator.OR);
                        operands.add(singleExpression);
                        whitespace();
                        singleExpression = singleExpression_();
                    } else if ((next >= $a && next <= $z) || (next >= $A && next <= $Z)
                               || next == $underscore || next == $backslash || next >= 0x80) {
                        if (singleExpression != null) {
                            if (spaceExpressions == null) spaceExpressions = new ArrayList<>();
                            spaceExpressions.add(singleExpression);
                            allowSlash = true;
                        }
                        singleExpression = identifierLike();
                    } else if (next == $comma) {
                        if (commaExpressions == null) commaExpressions = new ArrayList<>();
                        if (singleExpression == null) {
                            throw scanner.error("Expected expression.");
                        }
                        // Resolve space expressions
                        singleExpression = resolveAllOperations(operators, operands, singleExpression);
                        if (spaceExpressions != null && !spaceExpressions.isEmpty()) {
                            spaceExpressions.add(singleExpression);
                            singleExpression = new ListExpression(
                                    spaceExpressions, ListSeparator.SPACE,
                                    spaceExpressions.get(0).getSpan().expand(singleExpression.getSpan()));
                            spaceExpressions = null;
                        }
                        commaExpressions.add(singleExpression);
                        scanner.readChar();
                        allowSlash = true;
                        singleExpression = null;
                    } else {
                        break loop;
                    }
                    break;
                }
            }
        }

        if (bracketList) scanner.expectChar($rbracket);

        // Resolve remaining operators
        singleExpression = resolveAllOperations(operators, operands, singleExpression);

        // Resolve space expressions
        if (spaceExpressions != null && !spaceExpressions.isEmpty()) {
            if (singleExpression == null) throw scanner.error("Expected expression.");
            spaceExpressions.add(singleExpression);
            singleExpression = new ListExpression(
                    spaceExpressions, ListSeparator.SPACE,
                    spaceExpressions.get(0).getSpan().expand(singleExpression.getSpan()));
        }

        if (commaExpressions != null) {
            if (singleExpression != null) commaExpressions.add(singleExpression);
            inExpression = wasInExpression;
            return new ListExpression(
                    commaExpressions, ListSeparator.COMMA,
                    bracketList,
                    spanFrom(beforeBracket != null ? beforeBracket : start));
        } else if (bracketList) {
            inExpression = wasInExpression;
            if (spaceExpressions != null) {
                // Already collapsed above
            }
            return new ListExpression(
                    List.of(singleExpression), ListSeparator.UNDECIDED, true,
                    spanFrom(beforeBracket));
        } else {
            inExpression = wasInExpression;
            return singleExpression;
        }
    }

    private Expression resolveOneOperation(List<BinaryOperator> operators,
                                            List<Expression> operands,
                                            Expression right) {
        var op = operators.remove(operators.size() - 1);
        var left = operands.remove(operands.size() - 1);
        var span = left.getSpan().expand(right.getSpan());

        if (op == BinaryOperator.DIVIDED_BY && !inParentheses
                && isSlashOperand(left) && isSlashOperand(right)) {
            return new BinaryOperationExpression(op, left, right, true, span);
        }
        return new BinaryOperationExpression(op, left, right, span);
    }

    private Expression resolveAllOperations(@Nullable List<BinaryOperator> operators,
                                             @Nullable List<Expression> operands,
                                             Expression singleExpression) {
        if (operators == null || operators.isEmpty()) return singleExpression;
        while (!operators.isEmpty()) {
            singleExpression = resolveOneOperation(operators, operands, singleExpression);
        }
        return singleExpression;
    }

    private @Nullable Expression addSingleExpression(
            @Nullable Expression current, @Nullable List<Expression> space,
            @Nullable List<BinaryOperator> operators, @Nullable List<Expression> operands,
            boolean allowSlash, Expression newExpr, boolean wasInParentheses) {
        // Simplified: just return newExpr and let the caller handle space accumulation
        return newExpr;
    }

    private @Nullable List<Expression> extractSpaceExpressions(Expression expr,
                                                                @Nullable List<Expression> existing) {
        return existing;
    }

    private Expression extractSingleAfterSpace(Expression expr, @Nullable List<Expression> space) {
        return expr;
    }

    private boolean isSlashOperand(Expression expression) {
        return expression instanceof NumberExpression
                || expression instanceof FunctionExpression
                || (expression instanceof BinaryOperationExpression boe && boe.allowsSlash());
    }

    // =========================================================================
    // Single expression parsing
    // =========================================================================

    /**
     * Consumes an expression that doesn't contain any top-level whitespace.
     */
    private Expression singleExpression_() {
        int next = scanner.peekChar();
        if (next == -1) throw scanner.error("Expected expression.");
        if (next == $lparen) return parentheses();
        if (next == $slash) return unaryOperation();
        if (next == $dot) return number();
        if (next == $lbracket) return expressionInternal(false, true, null);
        if (next == $dollar) return variable();
        if (next == $ampersand) return selectorExpression();
        if (next == $singleQuote || next == $doubleQuote) return interpolatedString();
        if (next == $hash) return hashExpression();
        if (next == $plus) return plusExpression();
        if (next == $minus) return minusExpression();
        if (next == $exclamation) return importantExpression();
        if (next == $percent) return percentExpression();
        if (next >= $0 && next <= $9) return number();
        if ((next == $u || next == $U) && scanner.peekChar(1) == $plus) return unicodeRange();
        if ((next >= $a && next <= $z) || (next >= $A && next <= $Z)
                || next == $underscore || next == $backslash || next >= 0x80) {
            return identifierLike();
        }
        throw scanner.error("Expected expression.");
    }

    /**
     * Consumes a parenthesized expression.
     */
    protected Expression parentheses() {
        var wasInParentheses = inParentheses;
        inParentheses = true;
        try {
            var start = scanner.getState();
            scanner.expectChar($lparen);
            whitespace();
            if (!lookingAtExpression()) {
                scanner.expectChar($rparen);
                return new ListExpression(List.of(), ListSeparator.UNDECIDED, spanFrom(start));
            }

            var first = expressionUntilComma();
            if (scanner.scanChar($colon)) {
                whitespace();
                return map(first, start);
            }

            if (!scanner.scanChar($comma)) {
                scanner.expectChar($rparen);
                return new ParenthesizedExpression(first, spanFrom(start));
            }
            whitespace();

            var expressions = new ArrayList<Expression>();
            expressions.add(first);
            while (true) {
                if (!lookingAtExpression()) break;
                expressions.add(expressionUntilComma());
                if (!scanner.scanChar($comma)) break;
                whitespace();
            }

            scanner.expectChar($rparen);
            return new ParenthesizedExpression(
                    new ListExpression(expressions, ListSeparator.COMMA, spanFrom(start)),
                    spanFrom(start));
        } finally {
            inParentheses = wasInParentheses;
        }
    }

    private MapExpression map(Expression first, SpanScanner.ScannerState start) {
        var pairs = new ArrayList<ExpressionPair>();
        pairs.add(new ExpressionPair(first, expressionUntilComma()));

        while (scanner.scanChar($comma)) {
            whitespace();
            if (!lookingAtExpression()) break;
            var key = expressionUntilComma();
            scanner.expectChar($colon);
            whitespace();
            var value = expressionUntilComma();
            pairs.add(new ExpressionPair(key, value));
        }
        scanner.expectChar($rparen);
        return new MapExpression(pairs, spanFrom(start));
    }

    private Expression hashExpression() {
        if (scanner.peekChar(1) == $lbrace) return identifierLike();

        var start = scanner.getState();
        scanner.expectChar($hash);

        if (scanner.peekChar() != -1 && Characters.isDigit(scanner.peekChar())) {
            return new ColorExpression(hexColorContents(start), spanFrom(start));
        }

        var afterHash = scanner.getState();
        var ident = interpolatedIdentifier();
        if (isHexColor(ident)) {
            scanner.setState(afterHash);
            return new ColorExpression(hexColorContents(start), spanFrom(start));
        }

        var buffer = new InterpolationBuffer();
        buffer.writeCharCode($hash);
        buffer.addInterpolation(ident);
        return new StringExpression(buffer.buildInterpolation(spanFrom(start)));
    }

    private SassColor hexColorContents(SpanScanner.ScannerState start) {
        int digit1 = hexDigit();
        int digit2 = hexDigit();
        int digit3 = hexDigit();

        int red, green, blue;
        double alpha = 1.0;

        if (!isHexDigitAt(scanner.peekChar())) {
            // #abc
            red = (digit1 << 4) + digit1;
            green = (digit2 << 4) + digit2;
            blue = (digit3 << 4) + digit3;
        } else {
            int digit4 = hexDigit();
            if (!isHexDigitAt(scanner.peekChar())) {
                // #abcd
                red = (digit1 << 4) + digit1;
                green = (digit2 << 4) + digit2;
                blue = (digit3 << 4) + digit3;
                alpha = ((digit4 << 4) + digit4) / 255.0;
            } else {
                red = (digit1 << 4) + digit2;
                green = (digit3 << 4) + digit4;
                blue = (hexDigit() << 4) + hexDigit();
                if (isHexDigitAt(scanner.peekChar())) {
                    alpha = ((hexDigit() << 4) + hexDigit()) / 255.0;
                }
            }
        }
        // Preserve the original hex text (e.g., "#fff", "#ffffff") for
        // dart-sass-compatible serialization.
        String format = scanner.substring(start.position());
        return SassColor.rgbWithFormat(red, green, blue, alpha, format);
    }

    private boolean isHexColor(Interpolation interpolation) {
        var plain = interpolation.asPlain();
        if (plain == null) return false;
        int len = plain.length();
        if (len != 3 && len != 4 && len != 6 && len != 8) return false;
        for (int i = 0; i < len; i++) {
            if (!Characters.isHexDigit(plain.charAt(i))) return false;
        }
        return true;
    }

    private int hexDigit() {
        int c = scanner.peekChar();
        if (c != -1 && Characters.isHexDigit(c)) {
            scanner.readChar();
            return Characters.asHex(c);
        }
        throw scanner.error("Expected hex digit.");
    }

    private boolean isHexDigitAt(int c) {
        return c != -1 && Characters.isHexDigit(c);
    }

    private Expression plusExpression() {
        int next = scanner.peekChar(1);
        if (Characters.isDigit(next) || next == $dot) return number();
        return unaryOperation();
    }

    private Expression minusExpression() {
        int next = scanner.peekChar(1);
        if (Characters.isDigit(next) || next == $dot) return number();
        if (lookingAtInterpolatedIdentifier()) return identifierLike();
        return unaryOperation();
    }

    private Expression importantExpression() {
        var start = scanner.getState();
        scanner.readChar(); // consume !
        whitespace();
        expectIdentifier("important");
        return new StringExpression(
                new Interpolation(List.of("!important"), spanFrom(start)));
    }

    private Expression percentExpression() {
        var start = scanner.getState();
        scanner.readChar(); // consume %
        return new StringExpression(
                new Interpolation(List.of("%"), spanFrom(start)));
    }

    private UnaryOperationExpression unaryOperation() {
        var start = scanner.getState();
        int c = scanner.readChar();
        var op = switch (c) {
            case $plus -> UnaryOperator.PLUS;
            case $minus -> UnaryOperator.MINUS;
            case $slash -> UnaryOperator.DIVIDE;
            default -> throw scanner.error("Expected unary operator.");
        };
        whitespace();
        var operand = singleExpression_();
        return new UnaryOperationExpression(op, operand, spanFrom(start));
    }

    /**
     * Consumes a number expression.
     *
     * <p>Matches dart-sass's {@code _number()}, {@code _tryDecimal()}, and
     * {@code _tryExponent()} methods.</p>
     */
    protected NumberExpression number() {
        var start = scanner.getState();
        int first = scanner.peekChar();
        if (first == $plus || first == $minus) scanner.readChar();

        if (scanner.peekChar() != $dot) consumeNaturalNumber();

        // Try decimal — matches dart-sass _tryDecimal().
        // Don't complain about a dot after a number unless the number starts
        // with a dot. We don't allow a plain ".", but we need to allow "1."
        // so that "1..." will work as a rest argument.
        boolean allowTrailingDot = scanner.getPosition() != start.position()
                && first != $plus && first != $minus;
        if (scanner.peekChar() == $dot) {
            int next = scanner.peekChar(1);
            if (next != -1 && Characters.isDigit(next)) {
                scanner.readChar(); // consume dot
                while (scanner.peekChar() != -1 && Characters.isDigit(scanner.peekChar())) {
                    scanner.readChar();
                }
            } else if (!allowTrailingDot) {
                throw scanner.error("Expected digit.", scanner.getPosition() + 1, 1);
            }
        }

        // Try exponent
        int e = scanner.peekChar();
        if (e == $e || e == $E) {
            int next = scanner.peekChar(1);
            if (next != -1 && (Characters.isDigit(next) || next == $minus || next == $plus)) {
                scanner.readChar();
                if (next == $plus || next == $minus) scanner.readChar();
                if (scanner.peekChar() == -1 || !Characters.isDigit(scanner.peekChar())) {
                    throw scanner.error("Expected digit.");
                }
                while (scanner.peekChar() != -1 && Characters.isDigit(scanner.peekChar())) {
                    scanner.readChar();
                }
            }
        }

        double value = Double.parseDouble(scanner.substring(start.position()));
        String unit = null;
        if (scanner.scanChar($percent)) {
            unit = "%";
        } else if (lookingAtIdentifier()
                   && !(scanner.peekChar() == $minus && scanner.peekChar(1) == $minus)) {
            unit = identifier();
        }
        return new NumberExpression(value, unit, spanFrom(start));
    }

    private void consumeNaturalNumber() {
        if (!Characters.isDigit(scanner.readChar())) {
            throw scanner.error("Expected digit.", scanner.getPosition() - 1, 1);
        }
        while (scanner.peekChar() != -1 && Characters.isDigit(scanner.peekChar())) {
            scanner.readChar();
        }
    }

    private StringExpression unicodeRange() {
        var start = scanner.getState();
        expectIdentChar($u);
        scanner.expectChar($plus);
        int firstRangeLength = 0;
        while (scanner.peekChar() != -1 && Characters.isHexDigit(scanner.peekChar())) {
            scanner.readChar();
            firstRangeLength++;
        }
        boolean hasQuestionMark = false;
        while (scanner.scanChar($question)) {
            hasQuestionMark = true;
            firstRangeLength++;
        }
        if (firstRangeLength == 0) {
            throw scanner.error("Expected hex digit or \"?\".");
        } else if (firstRangeLength > 6) {
            throw error("Expected at most 6 digits.", spanFrom(start));
        } else if (hasQuestionMark) {
            return new StringExpression(
                    new Interpolation(List.of(scanner.substring(start.position())), spanFrom(start)));
        }
        if (scanner.scanChar($minus)) {
            int secondRangeLength = 0;
            while (scanner.peekChar() != -1 && Characters.isHexDigit(scanner.peekChar())) {
                scanner.readChar();
                secondRangeLength++;
            }
            if (secondRangeLength == 0) {
                throw scanner.error("Expected hex digit.");
            } else if (secondRangeLength > 6) {
                throw error("Expected at most 6 digits.", spanFrom(start));
            }
        }
        return new StringExpression(
                new Interpolation(List.of(scanner.substring(start.position())), spanFrom(start)));
    }

    private VariableExpression variable() {
        var start = scanner.getState();
        var name = variableName();
        return new VariableExpression(name, spanFrom(start));
    }

    private SelectorExpression selectorExpression() {
        var start = scanner.getState();
        scanner.expectChar($ampersand);
        return new SelectorExpression(spanFrom(start));
    }

    /**
     * Consumes a quoted string expression.
     */
    protected StringExpression interpolatedString() {
        var start = scanner.getState();
        int quote = scanner.readChar();
        if (quote != $singleQuote && quote != $doubleQuote) {
            throw scanner.error("Expected string.", start.position(), 1);
        }
        var buffer = new InterpolationBuffer();
        loop:
        while (true) {
            int next = scanner.peekChar();
            if (next == quote) {
                scanner.readChar();
                break loop;
            }
            switch (next) {
                case -1:
                    throw scanner.error("Expected " + (char) quote + ".");
                case $backslash:
                    int second = scanner.peekChar(1);
                    if (second != -1 && Characters.isNewline(second)) {
                        scanner.readChar();
                        scanner.readChar();
                        if (second == $cr) scanner.scanChar($lf);
                    } else {
                        buffer.writeCharCode(escapeCharacter());
                    }
                    break;
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addExpression(singleInterpolation());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                default:
                    if (Characters.isNewline(next)) {
                        throw scanner.error("Expected " + (char) quote + ".");
                    }
                    buffer.writeCharCode(scanner.readChar());
                    break;
            }
        }
        return new StringExpression(buffer.buildInterpolation(spanFrom(start)), true);
    }

    /**
     * Consumes an expression that starts like an identifier.
     */
    protected Expression identifierLike() {
        var start = scanner.getState();
        var ident = interpolatedIdentifier();
        var plain = ident.asPlain();
        if (plain != null) {
            if ("not".equals(plain)) {
                whitespace();
                var expr = singleExpression_();
                return new UnaryOperationExpression(
                        UnaryOperator.NOT, expr,
                        ident.getSpan().expand(expr.getSpan()));
            }

            if (scanner.peekChar() != $lparen) {
                switch (plain) {
                    case "false":
                        return new BooleanExpression(false, ident.getSpan());
                    case "null":
                        return new NullExpression(ident.getSpan());
                    case "true":
                        return new BooleanExpression(true, ident.getSpan());
                }

                String lower = plain.toLowerCase();
                var color = COLOR_NAMES_BY_NAME.get(lower);
                if (color != null) {
                    // Preserve the original name as format for dart-sass-
                    // compatible serialization.
                    var formatted = SassColor.rgbWithFormat(
                            color.getRed(), color.getGreen(), color.getBlue(),
                            color.getAlpha(), plain);
                    return new ColorExpression(formatted, ident.getSpan());
                }
            }

            // Try special function
            String lower = plain.toLowerCase();
            var specialFunction = trySpecialFunction(lower, start);
            if (specialFunction != null) return specialFunction;
        }

        // Check what follows
        int nextChar = scanner.peekChar();
        if (nextChar == $dot && scanner.peekChar(1) == $dot) {
            return new StringExpression(ident);
        }
        if (nextChar == $dot) {
            scanner.readChar();
            if (plain != null) return namespacedExpression(plain, start);
            throw error("Interpolation isn't allowed in namespaces.", ident.getSpan());
        }
        if (nextChar == $lparen && plain != null) {
            return new FunctionExpression(
                    plain, argumentInvocation(false), spanFrom(start));
        }
        if (nextChar == $lparen) {
            return new InterpolatedFunctionExpression(
                    ident, argumentInvocation(false), spanFrom(start));
        }
        return new StringExpression(ident);
    }

    private Expression namespacedExpression(String namespace, SpanScanner.ScannerState start) {
        if (scanner.peekChar() == $dollar) {
            var name = variableName();
            return new VariableExpression(name, namespace, spanFrom(start));
        }
        var funcName = identifier();
        return new FunctionExpression(
                funcName, funcName, argumentInvocation(false), namespace, spanFrom(start));
    }

    /**
     * If {@code name} is the name of a function with special syntax, consumes it.
     * Otherwise returns null.
     */
    protected @Nullable Expression trySpecialFunction(String name, SpanScanner.ScannerState start) {
        String normalized = unvendor(name);
        InterpolationBuffer buffer;
        switch (normalized) {
            case "element", "expression":
                if (!scanner.scanChar($lparen)) return null;
                buffer = new InterpolationBuffer();
                buffer.write(name);
                buffer.writeCharCode($lparen);
                break;
            case "calc":
                // Don't treat calc() as a special function — let it be parsed as
                // a regular FunctionExpression so Sass expressions inside it
                // (variables, function calls, arithmetic) are properly evaluated.
                return null;
            case "url":
                var contents = tryUrlContents(start);
                if (contents != null) {
                    return new StringExpression(contents);
                }
                return null;
            default:
                return null;
        }
        buffer.addInterpolation(interpolatedDeclarationValueAllowEmpty());
        scanner.expectChar($rparen);
        buffer.writeCharCode($rparen);
        return new StringExpression(buffer.buildInterpolation(spanFrom(start)));
    }

    /**
     * Like {@link #interpolatedDeclarationValue} but allows empty values.
     */
    private Interpolation interpolatedDeclarationValueAllowEmpty() {
        return interpolatedDeclarationValue(true, false, true, true, false);
    }

    /**
     * Like _tryUrlContents in the Dart source. Returns null if parsing fails.
     */
    protected @Nullable Interpolation tryUrlContents(SpanScanner.ScannerState start) {
        var beginningOfContents = scanner.getState();
        if (!scanner.scanChar($lparen)) return null;
        whitespaceWithoutComments();

        var buffer = new InterpolationBuffer();
        buffer.write("url");
        buffer.writeCharCode($lparen);
        loop:
        while (true) {
            int c = scanner.peekChar();
            switch (c) {
                case -1:
                    break loop;
                case $backslash:
                    buffer.write(escape(false));
                    break;
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addExpression(singleInterpolation());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                case $rparen:
                    buffer.writeCharCode(scanner.readChar());
                    return buffer.buildInterpolation(spanFrom(start));
                default:
                    if (Characters.isWhitespace(c)) {
                        whitespaceWithoutComments();
                        if (scanner.peekChar() != $rparen) break loop;
                    } else if (c == $exclamation || c == $percent || c == $ampersand
                               || (c >= $asterisk && c <= $tilde) || c >= 0x80) {
                        buffer.writeCharCode(scanner.readChar());
                    } else {
                        break loop;
                    }
                    break;
            }
        }
        scanner.setState(beginningOfContents);
        return null;
    }

    /**
     * Strips vendor prefixes from a CSS identifier.
     */
    protected String unvendor(String name) {
        if (name.length() < 2) return name;
        if (name.charAt(0) != '-') return name;
        if (name.charAt(1) == '-') return name;
        int i = name.indexOf('-', 2);
        if (i < 0) return name;
        return name.substring(i + 1);
    }

    // =========================================================================
    // Interpolation
    // =========================================================================

    /**
     * Consumes an identifier that may contain interpolation.
     */
    protected Interpolation interpolatedIdentifier() {
        var start = scanner.getState();
        var buffer = new InterpolationBuffer();

        if (scanner.scanChar($minus)) {
            buffer.writeCharCode($minus);
            if (scanner.scanChar($minus)) {
                buffer.writeCharCode($minus);
                interpolatedIdentifierBody(buffer);
                return buffer.buildInterpolation(spanFrom(start));
            }
        }

        int next = scanner.peekChar();
        if (next == -1) {
            throw scanner.error("Expected identifier.");
        } else if (Characters.isNameStart(next)) {
            buffer.writeCharCode(scanner.readChar());
        } else if (next == $backslash) {
            buffer.write(escape(false));
        } else if (next == $hash && scanner.peekChar(1) == $lbrace) {
            buffer.addExpression(singleInterpolation());
        } else {
            throw scanner.error("Expected identifier.");
        }

        interpolatedIdentifierBody(buffer);
        return buffer.buildInterpolation(spanFrom(start));
    }

    private void interpolatedIdentifierBody(InterpolationBuffer buffer) {
        while (true) {
            int next = scanner.peekChar();
            if (next == -1) break;
            if (next == $underscore || next == $minus
                    || Characters.isAlphabetic(next) || Characters.isDigit(next) || next >= 0x80) {
                buffer.writeCharCode(scanner.readChar());
            } else if (next == $backslash) {
                buffer.write(escape(false));
            } else if (next == $hash && scanner.peekChar(1) == $lbrace) {
                buffer.addExpression(singleInterpolation());
            } else {
                break;
            }
        }
    }

    /**
     * Consumes interpolation and returns the expression inside {@code #{...}}.
     */
    protected Expression singleInterpolation() {
        var start = scanner.getState();
        scanner.expect("#{");
        whitespace();
        var contents = expression();
        scanner.expectChar($rbrace);
        return contents;
    }

    /**
     * Consumes tokens up to "{", "}", ";", or "!" for selector-like content.
     */
    protected Interpolation almostAnyValue() {
        var start = scanner.getState();
        var buffer = new InterpolationBuffer();
        var brackets = new ArrayDeque<Integer>();

        loop:
        while (true) {
            int next = scanner.peekChar();
            switch (next) {
                case $backslash:
                    buffer.writeCharCode(scanner.readChar());
                    if (!scanner.isDone()) buffer.writeCharCode(scanner.readChar());
                    break;
                case $doubleQuote, $singleQuote:
                    buffer.addInterpolation(interpolatedStringToken());
                    break;
                case $slash:
                    if (scanner.peekChar(1) == $asterisk) {
                        buffer.write(rawText(this::loudComment));
                    } else if (scanner.peekChar(1) == $slash) {
                        buffer.write(rawText(this::silentComment));
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addInterpolation(interpolatedIdentifier());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                case $exclamation, $semicolon, $lbrace, $rbrace:
                    break loop;
                case $lparen, $lbracket:
                    int bracket = scanner.readChar();
                    buffer.writeCharCode(bracket);
                    brackets.push(Characters.opposite(bracket));
                    break;
                case $rparen, $rbracket:
                    if (brackets.isEmpty()) break loop;
                    int expected = brackets.pop();
                    scanner.expectChar(expected);
                    buffer.writeCharCode(expected);
                    break;
                case -1:
                    break loop;
                default:
                    if (lookingAtIdentifier()) {
                        buffer.write(identifier());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
            }
        }
        return buffer.buildInterpolation(spanFrom(start));
    }

    /**
     * Consumes a raw interpolated string token (for strings that should not be
     * semantically processed).
     */
    protected Interpolation interpolatedStringToken() {
        var start = scanner.getState();
        int quote = scanner.readChar();
        if (quote != $singleQuote && quote != $doubleQuote) {
            throw scanner.error("Expected string.", start.position(), 1);
        }
        var buffer = new InterpolationBuffer();
        buffer.writeCharCode(quote);
        loop:
        while (true) {
            int next = scanner.peekChar();
            if (next == quote) {
                buffer.writeCharCode(scanner.readChar());
                break loop;
            }
            switch (next) {
                case -1:
                    throw scanner.error("Expected " + (char) quote + ".");
                case $backslash:
                    buffer.writeCharCode(scanner.readChar());
                    if (!scanner.isDone()) buffer.writeCharCode(scanner.readChar());
                    break;
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addExpression(singleInterpolation());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                default:
                    if (Characters.isNewline(next)) {
                        throw scanner.error("Expected " + (char) quote + ".");
                    }
                    buffer.writeCharCode(scanner.readChar());
                    break;
            }
        }
        return buffer.buildInterpolation(spanFrom(start));
    }

    /**
     * Consumes the value of a declaration, which may contain interpolation.
     */
    protected Interpolation interpolatedDeclarationValue(boolean silentComments) {
        return interpolatedDeclarationValue(false, false, true, true, silentComments);
    }

    /**
     * Full-parameter version of interpolated declaration value parsing.
     */
    protected Interpolation interpolatedDeclarationValue(boolean allowEmpty, boolean allowSemicolon,
                                                          boolean allowColon, boolean allowOpenBrace,
                                                          boolean silentComments) {
        var start = scanner.getState();
        var buffer = new InterpolationBuffer();
        var brackets = new ArrayDeque<Integer>();
        loop:
        while (true) {
            int next = scanner.peekChar();
            switch (next) {
                case $backslash:
                    buffer.write(escape(false));
                    break;
                case $doubleQuote, $singleQuote:
                    buffer.addInterpolation(interpolatedStringToken());
                    break;
                case $slash:
                    if (scanner.peekChar(1) == $asterisk) {
                        buffer.write(rawText(this::loudComment));
                    } else if (scanner.peekChar(1) == $slash && silentComments) {
                        silentComment();
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addInterpolation(interpolatedIdentifier());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
                case $colon:
                    if (!allowColon && brackets.isEmpty()) break loop;
                    buffer.writeCharCode(scanner.readChar());
                    break;
                case $lbrace:
                    if (!allowOpenBrace) break loop;
                    buffer.writeCharCode(scanner.readChar());
                    brackets.push($rbrace);
                    break;
                case $lparen, $lbracket:
                    int bracket = scanner.readChar();
                    buffer.writeCharCode(bracket);
                    brackets.push(Characters.opposite(bracket));
                    break;
                case $rparen, $rbrace, $rbracket:
                    if (brackets.isEmpty()) break loop;
                    int expected = brackets.pop();
                    scanner.expectChar(expected);
                    buffer.writeCharCode(expected);
                    break;
                case $semicolon:
                    if (!allowSemicolon && brackets.isEmpty()) break loop;
                    buffer.writeCharCode(scanner.readChar());
                    break;
                case -1:
                    break loop;
                default:
                    if (lookingAtIdentifier()) {
                        buffer.write(identifier());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;
            }
        }
        if (!brackets.isEmpty()) {
            scanner.expectChar(brackets.peek());
        }
        if (!allowEmpty && buffer.isEmpty()) {
            throw scanner.error("Expected token.");
        }
        return buffer.buildInterpolation(spanFrom(start));
    }

    // =========================================================================
    // Character classification helpers
    // =========================================================================

    /**
     * Returns whether the scanner is immediately before an identifier that may
     * contain interpolation.
     */
    private boolean lookingAtInterpolatedIdentifier() {
        int next = scanner.peekChar();
        if (next == -1) return false;
        if (Characters.isNameStart(next) || next == $backslash) return true;
        if (next == $hash) return scanner.peekChar(1) == $lbrace;
        if (next == $minus) {
            int second = scanner.peekChar(1);
            if (second == -1) return false;
            if (second == $hash) return scanner.peekChar(2) == $lbrace;
            return Characters.isNameStart(second) || second == $backslash || second == $minus;
        }
        return false;
    }

    private boolean lookingAtInterpolatedIdentifierBody() {
        int next = scanner.peekChar();
        if (next == -1) return false;
        if (Characters.isName(next) || next == $backslash) return true;
        if (next == $hash) return scanner.peekChar(1) == $lbrace;
        return false;
    }

    /**
     * Returns whether the scanner is immediately before a SassScript expression.
     */
    protected boolean lookingAtExpression() {
        int next = scanner.peekChar();
        if (next == -1) return false;
        if (next == $dot) return scanner.peekChar(1) != $dot;
        if (next == $exclamation) {
            int second = scanner.peekChar(1);
            return second == -1 || second == $i || second == $I
                   || Characters.isWhitespace(second);
        }
        return next == $lparen || next == $slash || next == $lbracket
               || next == $singleQuote || next == $doubleQuote
               || next == $hash || next == $plus || next == $minus
               || next == $backslash || next == $dollar || next == $ampersand
               || next == $percent
               || Characters.isNameStart(next) || Characters.isDigit(next);
    }

    private boolean lookingAtPotentialPropertyHack() {
        int next = scanner.peekChar();
        if (next == $colon || next == $asterisk || next == $dot) return true;
        if (next == $hash) return scanner.peekChar(1) != $lbrace;
        return false;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Adds an expression to an interpolation buffer. If it's an unquoted string,
     * adds its interpolation contents instead.
     */
    private void addOrInject(InterpolationBuffer buffer, Expression expression) {
        if (expression instanceof StringExpression strExpr && !strExpr.hasQuotes()) {
            buffer.addInterpolation(strExpr.getText());
        } else {
            buffer.addExpression(expression);
        }
    }

    /**
     * Checks whether a name starts with a private prefix ('-' or '_').
     */
    protected boolean isPrivate(String identifier) {
        if (identifier.isEmpty()) return false;
        int first = identifier.charAt(0);
        return first == $minus || first == $underscore;
    }

    /**
     * Case-insensitive string comparison for ASCII strings.
     */
    protected boolean equalsIgnoreCase(String a, String b) {
        return a.equalsIgnoreCase(b);
    }
}
