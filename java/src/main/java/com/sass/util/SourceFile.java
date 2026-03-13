package com.sass.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A representation of a source file, providing line/column information.
 * Port of Dart's source_span SourceFile.
 */
public final class SourceFile {
    private final URI url;
    private final String text;
    private final List<Integer> lineStarts;

    public SourceFile(String text, URI url) {
        this.text = text;
        this.url = url;
        this.lineStarts = computeLineStarts(text);
    }

    public SourceFile(String text) {
        this(text, null);
    }

    public URI url() { return url; }
    public String getText() { return text; }
    public String getText(int start) { return text.substring(start); }
    public String getText(int start, int end) { return text.substring(start, end); }
    public int length() { return text.length(); }
    public int lines() { return lineStarts.size(); }

    /** Returns the 0-based line number for the given offset. */
    public int getLine(int offset) {
        if (offset < 0 || offset > text.length()) {
            throw new IndexOutOfBoundsException(
                    "Offset " + offset + " is out of range for source of length " + text.length());
        }
        if (offset == text.length()) {
            return lineStarts.size() - 1;
        }
        int lo = 0, hi = lineStarts.size() - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (lineStarts.get(mid) <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Returns the 0-based column number for the given offset. */
    public int getColumn(int offset) {
        int line = getLine(offset);
        return offset - lineStarts.get(line);
    }

    /** Returns the offset for the start of the given line. */
    public int getOffset(int line) {
        return lineStarts.get(line);
    }

    /** Creates a SourceLocation for the given offset. */
    public SourceLocation location(int offset) {
        int line = getLine(offset);
        int column = offset - lineStarts.get(line);
        return new SourceLocation(offset, line, column, this);
    }

    /** Creates a FileSpan from {@code start} to {@code end}. */
    public FileSpan span(int start, int end) {
        return new FileSpan(location(start), location(end), this);
    }

    /** Creates a FileSpan for the given location (zero-length). */
    public FileSpan span(int offset) {
        return span(offset, offset);
    }

    private static List<Integer> computeLineStarts(String text) {
        var starts = new ArrayList<Integer>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            } else if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    starts.add(i + 2);
                    i++; // skip the \n
                } else {
                    starts.add(i + 1);
                }
            }
        }
        return starts;
    }
}
