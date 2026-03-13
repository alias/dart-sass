package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;

import java.util.List;

/**
 * A string with interpolated expressions.
 *
 * <p>The {@link #contents} list alternates between {@link String} and
 * {@link Expression} objects.
 */
public final class Interpolation implements SassNode {

    private final List<Object> contents;
    private final FileSpan span;

    public Interpolation(List<Object> contents, FileSpan span) {
        this.contents = List.copyOf(contents);
        this.span = span;
    }

    public List<Object> getContents() {
        return contents;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /** Returns {@code true} if this contains no {@link Expression} elements. */
    public boolean isPlain() {
        return contents.stream().allMatch(c -> c instanceof String);
    }

    /**
     * If this interpolation contains no expressions, returns the plain text.
     * Otherwise returns {@code null}.
     */
    public String asPlain() {
        if (!isPlain()) {
            return null;
        }
        var sb = new StringBuilder();
        for (Object c : contents) {
            sb.append((String) c);
        }
        return sb.toString();
    }

    /** Returns the text before the first expression, or the entire text if plain. */
    public String initialPlain() {
        var sb = new StringBuilder();
        for (Object c : contents) {
            if (c instanceof String s) {
                sb.append(s);
            } else {
                break;
            }
        }
        return sb.toString();
    }
}
