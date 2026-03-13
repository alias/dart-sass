package com.sass.ast.sass;

import com.sass.util.FileSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A callable declaration (function or mixin) with a name, parameters, and body.
 */
public abstract class CallableDeclaration extends ParentStatement {

    private final String name;
    private final String originalName;
    private final ArgumentDeclaration parameters;
    private final @Nullable SilentComment comment;

    protected CallableDeclaration(
            String name,
            String originalName,
            ArgumentDeclaration parameters,
            @Nullable SilentComment comment,
            List<Statement> children,
            boolean hasDeclarations) {
        super(children, hasDeclarations);
        this.name = name;
        this.originalName = originalName;
        this.parameters = parameters;
        this.comment = comment;
    }

    /** The normalized name of this callable. */
    public String getName() {
        return name;
    }

    /** The original, un-normalized name as written in the source. */
    public String getOriginalName() {
        return originalName;
    }

    public ArgumentDeclaration getParameters() {
        return parameters;
    }

    /** The silent comment immediately preceding this declaration, if any. */
    public @Nullable SilentComment getComment() {
        return comment;
    }
}
