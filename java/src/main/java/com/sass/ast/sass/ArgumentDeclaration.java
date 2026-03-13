package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A declaration of arguments for a callable (mixin or function).
 */
public final class ArgumentDeclaration implements SassNode {

    private final List<Argument> arguments;
    private final @Nullable String restArgument;
    private final FileSpan span;

    public ArgumentDeclaration(List<Argument> arguments, @Nullable String restArgument, FileSpan span) {
        this.arguments = List.copyOf(arguments);
        this.restArgument = restArgument;
        this.span = span;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public @Nullable String getRestArgument() {
        return restArgument;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /** Returns {@code true} if this declaration has no arguments and no rest argument. */
    public boolean isEmpty() {
        return arguments.isEmpty() && restArgument == null;
    }
}
