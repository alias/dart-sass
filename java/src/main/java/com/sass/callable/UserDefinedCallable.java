package com.sass.callable;

import com.sass.ast.sass.CallableDeclaration;
import com.sass.environment.Environment;

/**
 * A callback defined in the user's Sass stylesheet.
 *
 * <p>This represents a user-defined function or mixin, capturing both
 * the declaration (name, parameters, body) and the environment (closure)
 * in which it was declared.
 */
public final class UserDefinedCallable implements SassCallable {

    private final CallableDeclaration declaration;
    private final Environment environment;
    private final boolean inDependency;

    /**
     * Creates a new user-defined callable.
     *
     * @param declaration the callable declaration (function or mixin rule)
     * @param environment the environment in which this callable was declared (closure)
     * @param inDependency whether this callable was defined in a dependency
     */
    public UserDefinedCallable(CallableDeclaration declaration,
                                Environment environment,
                                boolean inDependency) {
        this.declaration = declaration;
        this.environment = environment;
        this.inDependency = inDependency;
    }

    /** The callable declaration, containing name, parameters, and body. */
    public CallableDeclaration getDeclaration() {
        return declaration;
    }

    /** The environment in which this callable was declared. */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Whether this callable was defined in a dependency.
     *
     * <p>That is, whether this was (transitively) loaded through a load path or
     * importer rather than relative to the entrypoint.
     */
    public boolean isInDependency() {
        return inDependency;
    }

    @Override
    public String getName() {
        return declaration.getName();
    }
}
