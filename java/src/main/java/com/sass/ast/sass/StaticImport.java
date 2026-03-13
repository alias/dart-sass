package com.sass.ast.sass;

import com.sass.util.FileSpan;
import org.jspecify.annotations.Nullable;

/**
 * A static CSS import that is emitted as-is in the output.
 */
public final class StaticImport implements Import {

    private final Interpolation url;
    private final @Nullable Interpolation modifiers;
    private final FileSpan span;

    public StaticImport(Interpolation url, @Nullable Interpolation modifiers, FileSpan span) {
        this.url = url;
        this.modifiers = modifiers;
        this.span = span;
    }

    public Interpolation getUrl() {
        return url;
    }

    public @Nullable Interpolation getModifiers() {
        return modifiers;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }
}
