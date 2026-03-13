package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * An invocation of arguments passed to a mixin or function call.
 */
public final class ArgumentInvocation implements SassNode {

    /** An empty argument invocation with no positional, named, or rest arguments. */
    public static final ArgumentInvocation EMPTY =
            new ArgumentInvocation(List.of(), Map.of(), null, null,
                    new FileSpan(
                            new com.sass.util.SourceLocation(0, 0, 0, null),
                            new com.sass.util.SourceLocation(0, 0, 0, null),
                            new com.sass.util.SourceFile("", null)));

    private final List<Expression> positional;
    private final Map<String, Expression> named;
    private final @Nullable Expression rest;
    private final @Nullable Expression keywordRest;
    private final FileSpan span;

    public ArgumentInvocation(
            List<Expression> positional,
            Map<String, Expression> named,
            @Nullable Expression rest,
            @Nullable Expression keywordRest,
            FileSpan span) {
        this.positional = List.copyOf(positional);
        this.named = Map.copyOf(named);
        this.rest = rest;
        this.keywordRest = keywordRest;
        this.span = span;
    }

    public List<Expression> getPositional() {
        return positional;
    }

    public Map<String, Expression> getNamed() {
        return named;
    }

    public @Nullable Expression getRest() {
        return rest;
    }

    public @Nullable Expression getKeywordRest() {
        return keywordRest;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /** Returns {@code true} if there are no positional, named, or rest arguments. */
    public boolean isEmpty() {
        return positional.isEmpty() && named.isEmpty() && rest == null;
    }
}
