package com.sass.parse;

import com.sass.ast.sass.Expression;
import com.sass.ast.sass.Interpolation;
import com.sass.util.FileSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * A buffer that iteratively builds up an {@link Interpolation}.
 *
 * <p>Add text using {@link #write} and related methods, and {@link Expression}s
 * using {@link #addExpression}. Once that's done, call {@link #buildInterpolation}
 * to build the result.
 *
 * <p>Port of {@code lib/src/interpolation_buffer.dart}.
 */
public final class InterpolationBuffer {

    /** The buffer that accumulates plain text. */
    private final StringBuilder text = new StringBuilder();

    /**
     * The contents of the {@link Interpolation} so far.
     * This contains {@link String}s and {@link Expression}s.
     */
    private final List<Object> contents = new ArrayList<>();

    /** Returns whether this buffer has no contents. */
    public boolean isEmpty() {
        return contents.isEmpty() && text.isEmpty();
    }

    /** Returns the substring of the buffer string after the last interpolation. */
    public String trailingString() {
        return text.toString();
    }

    /** Empties this buffer. */
    public void clear() {
        contents.clear();
        text.setLength(0);
    }

    /** Writes a string to this buffer. */
    public void write(String s) {
        text.append(s);
    }

    /** Writes a string representation of an object to this buffer. */
    public void write(Object obj) {
        text.append(obj);
    }

    /** Writes a single character code to this buffer. */
    public void writeCharCode(int character) {
        text.append((char) character);
    }

    /** Writes a string followed by a newline. */
    public void writeln() {
        text.append('\n');
    }

    /** Writes a string followed by a newline. */
    public void writeln(String s) {
        text.append(s);
        text.append('\n');
    }

    /**
     * Adds an expression to this buffer.
     *
     * <p>The {@code span} should cover from the beginning of {@code #{} through {@code }}.
     */
    public void addExpression(Expression expression) {
        flushText();
        contents.add(expression);
    }

    /**
     * Adds the contents of an existing {@link Interpolation} to this buffer.
     */
    public void addInterpolation(Interpolation interpolation) {
        List<Object> interpContents = interpolation.getContents();
        if (interpContents.isEmpty()) return;

        int startIndex = 0;
        // If the first element is a string, merge it into our text buffer
        if (interpContents.get(0) instanceof String s) {
            text.append(s);
            startIndex = 1;
        }

        if (startIndex < interpContents.size()) {
            flushText();
            for (int i = startIndex; i < interpContents.size(); i++) {
                contents.add(interpContents.get(i));
            }
            // If the last element is a string, pull it back into text
            if (!contents.isEmpty() && contents.get(contents.size() - 1) instanceof String s) {
                contents.remove(contents.size() - 1);
                text.append(s);
            }
        }
    }

    /** Flushes accumulated text to the contents list. */
    private void flushText() {
        if (text.isEmpty()) return;
        contents.add(text.toString());
        text.setLength(0);
    }

    /**
     * Creates an {@link Interpolation} with the given span from the contents
     * of this buffer.
     */
    public Interpolation buildInterpolation(FileSpan span) {
        List<Object> result = new ArrayList<>(contents);
        if (!text.isEmpty()) {
            result.add(text.toString());
        }
        return new Interpolation(result, span);
    }

    @Override
    public String toString() {
        var buffer = new StringBuilder();
        for (Object element : contents) {
            if (element instanceof String s) {
                buffer.append(s);
            } else {
                buffer.append("#{");
                buffer.append(element);
                buffer.append('}');
            }
        }
        buffer.append(text);
        return buffer.toString();
    }
}
