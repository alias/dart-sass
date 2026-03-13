package com.sass.parse;

import com.sass.ast.sass.*;
import com.sass.util.Characters;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.sass.parse.CharCodes.*;

/**
 * A parser for the CSS-compatible SCSS syntax.
 *
 * <p>Port of {@code lib/src/parse/scss.dart}.
 */
public final class ScssParser extends StylesheetParser {

    // =========================================================================
    // Constructor
    // =========================================================================

    public ScssParser(String contents, @Nullable URI url) {
        super(contents, url);
    }

    public ScssParser(String contents) {
        this(contents, null);
    }

    // =========================================================================
    // Abstract method implementations
    // =========================================================================

    @Override
    protected boolean isIndented() {
        return false;
    }

    @Override
    protected int currentIndentation() {
        return 0;
    }

    @Override
    protected Interpolation styleRuleSelector() {
        return almostAnyValue();
    }

    @Override
    protected void expectStatementSeparator(@Nullable String name) {
        whitespaceWithoutComments();
        if (scanner.isDone()) return;
        int next = scanner.peekChar();
        if (next == $semicolon || next == $rbrace) return;
        scanner.expectChar($semicolon);
    }

    @Override
    protected boolean atEndOfStatement() {
        int next = scanner.peekChar();
        return next == -1
                || next == $semicolon
                || next == $rbrace
                || next == $lbrace;
    }

    @Override
    protected boolean lookingAtChildren() {
        return scanner.peekChar() == $lbrace;
    }

    @Override
    protected boolean scanElse(int ifIndentation) {
        var start = scanner.getState();
        whitespace();
        var beforeAt = scanner.getState();
        if (scanner.scanChar($at)) {
            if (scanIdentifier("else", true)) return true;
            if (scanIdentifier("elseif", true)) {
                // @elseif is deprecated. In the Dart source this emits a
                // deprecation warning. For now, we silently accept it and
                // rewind past the "if" so the caller sees "@else if".
                scanner.setPosition(scanner.getPosition() - 2);
                return true;
            }
        }
        scanner.setState(start);
        return false;
    }

    @Override
    protected List<Statement> children(Supplier<Statement> child) {
        scanner.expectChar($lbrace);
        whitespaceWithoutComments();
        var children = new ArrayList<Statement>();
        while (true) {
            whitespaceWithoutComments();
            int next = scanner.peekChar();
            switch (next) {
                case $dollar:
                    children.add(variableDeclarationWithoutNamespace());
                    break;
                case $slash:
                    int secondChar = scanner.peekChar(1);
                    if (secondChar == $slash) {
                        children.add(silentCommentStatement());
                        whitespaceWithoutComments();
                    } else if (secondChar == $asterisk) {
                        children.add(loudCommentStatement());
                        whitespaceWithoutComments();
                    } else {
                        children.add(child.get());
                    }
                    break;
                case $semicolon:
                    scanner.readChar();
                    whitespaceWithoutComments();
                    break;
                case $rbrace:
                    scanner.expectChar($rbrace);
                    return children;
                default:
                    children.add(child.get());
                    break;
            }
        }
    }

    @Override
    protected List<Statement> statements(Supplier<@Nullable Statement> statement) {
        var statements = new ArrayList<Statement>();
        whitespaceWithoutComments();
        while (!scanner.isDone()) {
            whitespaceWithoutComments();
            if (scanner.isDone()) break;
            int next = scanner.peekChar();
            switch (next) {
                case $dollar:
                    statements.add(variableDeclarationWithoutNamespace());
                    break;
                case $slash:
                    int secondChar = scanner.peekChar(1);
                    if (secondChar == $slash) {
                        statements.add(silentCommentStatement());
                        whitespaceWithoutComments();
                    } else if (secondChar == $asterisk) {
                        statements.add(loudCommentStatement());
                        whitespaceWithoutComments();
                    } else {
                        var child = statement.get();
                        if (child != null) statements.add(child);
                    }
                    break;
                case $semicolon:
                    scanner.readChar();
                    whitespaceWithoutComments();
                    break;
                default:
                    var child = statement.get();
                    if (child != null) statements.add(child);
                    break;
            }
        }
        return statements;
    }

    // =========================================================================
    // Comment parsing
    // =========================================================================

    /**
     * Consumes a statement-level silent comment block.
     *
     * <p>Port of {@code _silentComment} in scss.dart.
     */
    private SilentComment silentCommentStatement() {
        var start = scanner.getState();
        scanner.expect("//");

        do {
            // Read characters until we consume a newline or reach the end.
            // The Dart source uses readChar() in the loop condition, so the
            // newline character itself is consumed as part of the loop.
            while (!scanner.isDone()) {
                int c = scanner.readChar();
                if (Characters.isNewline(c)) break;
            }
            if (scanner.isDone()) break;
            spaces();
        } while (scanner.scan("//"));

        // In the Dart source, this also checks plainCss and throws
        // "Silent comments aren't allowed in plain CSS." That field is not
        // yet available in this Java port.

        lastSilentComment = new SilentComment(
                scanner.substring(start.position()),
                spanFrom(start));
        return lastSilentComment;
    }

    /**
     * Consumes a statement-level loud comment block.
     *
     * <p>Port of {@code _loudComment} in scss.dart.
     */
    private LoudComment loudCommentStatement() {
        var start = scanner.getState();
        scanner.expect("/*");
        var buffer = new InterpolationBuffer();
        buffer.write("/*");
        loop:
        while (true) {
            int next = scanner.peekChar();
            switch (next) {
                case $hash:
                    if (scanner.peekChar(1) == $lbrace) {
                        buffer.addExpression(singleInterpolation());
                    } else {
                        buffer.writeCharCode(scanner.readChar());
                    }
                    break;

                case $asterisk:
                    buffer.writeCharCode(scanner.readChar());
                    if (scanner.peekChar() != $slash) continue loop;
                    buffer.writeCharCode(scanner.readChar());
                    return new LoudComment(buffer.buildInterpolation(spanFrom(start)));

                case $cr:
                    scanner.readChar();
                    if (scanner.peekChar() != $lf) {
                        buffer.writeCharCode($lf);
                    }
                    break;

                case $ff:
                    scanner.readChar();
                    buffer.writeCharCode($lf);
                    break;

                case -1:
                    throw scanner.error("expected more input.");

                default:
                    buffer.writeCharCode(scanner.readChar());
                    break;
            }
        }
    }
}
