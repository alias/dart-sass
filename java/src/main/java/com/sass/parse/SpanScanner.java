package com.sass.parse;

import com.sass.util.FileSpan;
import com.sass.util.SourceFile;
import com.sass.util.SourceLocation;

import java.net.URI;

/**
 * A character-by-character scanner with source span tracking.
 * Port of Dart's string_scanner SpanScanner.
 */
public class SpanScanner {
    private final SourceFile sourceFile;
    private final String source;
    private int position;

    public SpanScanner(String source, URI url) {
        this.source = source;
        this.sourceFile = new SourceFile(source, url);
        this.position = 0;
    }

    public SpanScanner(String source) {
        this(source, (URI) null);
    }

    public String getSource() { return source; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public int length() { return source.length(); }
    public boolean isDone() { return position >= source.length(); }
    public SourceFile getSourceFile() { return sourceFile; }

    /** Returns the character at the current position without consuming it. Returns -1 at end. */
    public int peekChar() {
        if (isDone()) return -1;
        return source.charAt(position);
    }

    /** Returns the character at the given offset from the current position. Returns -1 if out of bounds. */
    public int peekChar(int offset) {
        int index = position + offset;
        if (index < 0 || index >= source.length()) return -1;
        return source.charAt(index);
    }

    /** Reads and returns the character at the current position, advancing by one. */
    public int readChar() {
        if (isDone()) {
            throw new StringIndexOutOfBoundsException("Expected more input.");
        }
        return source.charAt(position++);
    }

    /** Consumes the character at the current position if it matches {@code expected}. */
    public boolean scanChar(int expected) {
        if (isDone() || source.charAt(position) != expected) return false;
        position++;
        return true;
    }

    /** Asserts that the current character matches {@code expected} and advances. */
    public void expectChar(int expected) {
        if (!scanChar(expected)) {
            throw error("Expected '" + (char) expected + "'.");
        }
    }

    /** Attempts to match {@code text} at the current position. */
    public boolean scan(String text) {
        if (position + text.length() > source.length()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (source.charAt(position + i) != text.charAt(i)) return false;
        }
        position += text.length();
        return true;
    }

    /** Asserts that {@code text} matches at the current position. */
    public void expect(String text) {
        if (!scan(text)) {
            throw error("Expected \"" + text + "\".");
        }
    }

    /** Scans a case-insensitive match of {@code text}. */
    public boolean scanIgnoreCase(String text) {
        if (position + text.length() > source.length()) return false;
        for (int i = 0; i < text.length(); i++) {
            char actual = source.charAt(position + i);
            char expected = text.charAt(i);
            if (Character.toLowerCase(actual) != Character.toLowerCase(expected)) return false;
        }
        position += text.length();
        return true;
    }

    /** Returns the substring from {@code start} to the current position. */
    public String substring(int start) {
        return source.substring(start, position);
    }

    /** Returns the substring from {@code start} to {@code end}. */
    public String substring(int start, int end) {
        return source.substring(start, end);
    }

    /** Returns the current scanner state for later restoration. */
    public ScannerState getState() {
        return new ScannerState(position);
    }

    /** Restores the scanner to a previously saved state. */
    public void setState(ScannerState state) {
        this.position = state.position();
    }

    /** Returns a FileSpan from {@code start} to the current position. */
    public FileSpan spanFrom(ScannerState start) {
        return sourceFile.span(start.position(), position);
    }

    /** Returns a FileSpan from {@code start} to {@code end}. */
    public FileSpan spanFrom(ScannerState start, ScannerState end) {
        return sourceFile.span(start.position(), end.position());
    }

    /** Returns a zero-length FileSpan at the current position. */
    public FileSpan emptySpan() {
        return sourceFile.span(position, position);
    }

    /** Returns a SourceLocation for the current position. */
    public SourceLocation location() {
        return sourceFile.location(position);
    }

    /** Creates an error with the current position context. */
    public RuntimeException error(String message) {
        return error(message, position, 0);
    }

    /** Returns the 0-based line number for the current position. */
    public int getLine() {
        return sourceFile.getLine(position);
    }

    /** Asserts that the scanner has reached the end of input. */
    public void expectDone() {
        if (!isDone()) {
            throw error("Expected end of input.");
        }
    }

    /** Asserts that the current character matches {@code expected}, with a custom name. */
    public void expectChar(int expected, String name) {
        if (!scanChar(expected)) {
            throw error("Expected " + name + ".");
        }
    }

    /** Creates an error at the given position. */
    public RuntimeException error(String message, int start, int length) {
        FileSpan span = sourceFile.span(start, start + length);
        return new com.sass.exception.SassFormatException(message, span);
    }

    /** Scanner state record for save/restore. */
    public record ScannerState(int position) {}
}
