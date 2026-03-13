package com.sass.util;

import java.net.URI;

/**
 * A span of characters in a source file.
 * Port of Dart's source_span FileSpan.
 */
public record FileSpan(SourceLocation start, SourceLocation end, SourceFile file) {

    /** The source text covered by this span. */
    public String text() {
        return file.getText(start.offset(), end.offset());
    }

    /** The length of this span. */
    public int length() {
        return end.offset() - start.offset();
    }

    /** The URI of the source file. */
    public URI sourceUrl() {
        return file.url();
    }

    /** Returns a span from the start of this span to the end of {@code other}. */
    public FileSpan expand(FileSpan other) {
        SourceLocation newStart = start.offset() < other.start().offset() ? start : other.start();
        SourceLocation newEnd = end.offset() > other.end().offset() ? end : other.end();
        return new FileSpan(newStart, newEnd, file);
    }

    /**
     * Returns a highlighted representation of this span in its source file.
     * This is a simplified version — the full Dart implementation is more complex.
     */
    public String highlight() {
        return highlight(false);
    }

    public String highlight(boolean color) {
        var sb = new StringBuilder();
        int line = start.line();
        int endLine = end.line();

        // Show context
        String lineText = getLineText(line);
        sb.append(formatLineNumber(line + 1)).append(" | ").append(lineText).append("\n");

        // Show the underline
        int col = start.column();
        int endCol = (line == endLine) ? end.column() : lineText.length();
        int underlineLen = Math.max(1, endCol - col);
        sb.append(repeatChar(' ', formatLineNumber(line + 1).length()))
                .append(" | ")
                .append(repeatChar(' ', col))
                .append(repeatChar('^', underlineLen));

        return sb.toString();
    }

    /** Returns the text of a given line in the source file. */
    private String getLineText(int line) {
        int start = file.getOffset(line);
        int end;
        if (line + 1 < file.lines()) {
            end = file.getOffset(line + 1);
            // Strip trailing newline
            if (end > start && file.getText().charAt(end - 1) == '\n') end--;
            if (end > start && file.getText().charAt(end - 1) == '\r') end--;
        } else {
            end = file.length();
        }
        return file.getText(start, end);
    }

    private static String formatLineNumber(int lineNumber) {
        return String.valueOf(lineNumber);
    }

    private static String repeatChar(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }

    @Override
    public String toString() {
        var url = sourceUrl();
        var prefix = url != null ? url.toString() + ":" : "";
        return prefix + (start.line() + 1) + ":" + (start.column() + 1);
    }
}
