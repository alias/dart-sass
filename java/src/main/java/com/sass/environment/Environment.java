package com.sass.environment;

import com.sass.callable.Callable;
import com.sass.module.Module;
import com.sass.value.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The lexical environment during Sass evaluation.
 *
 * <p>Tracks variables, functions, and mixins at various scope levels using
 * a stack of maps. Supports creating new scopes and closures.</p>
 */
public final class Environment {

    private final List<Map<String, Value>> variables;
    private final List<Map<String, Callable>> functions;
    private final List<Map<String, Callable>> mixins;

    // Content callable for @content blocks
    private @Nullable UserDefinedContent content;

    // Module system: namespaced modules (from @use 'foo' as ns)
    private final Map<String, Module> modules = new HashMap<>();

    // Modules imported without a namespace (@use 'foo' as * or @import)
    private final List<Module> globalModules = new ArrayList<>();

    // Modules forwarded via @forward
    private final List<Module> forwardedModules = new ArrayList<>();

    /** Creates a new empty root environment. */
    public Environment() {
        this.variables = new ArrayList<>();
        this.variables.add(new HashMap<>());
        this.functions = new ArrayList<>();
        this.functions.add(new HashMap<>());
        this.mixins = new ArrayList<>();
        this.mixins.add(new HashMap<>());
    }

    /** Creates an environment from existing scope stacks (for closures). */
    private Environment(
            List<Map<String, Value>> variables,
            List<Map<String, Callable>> functions,
            List<Map<String, Callable>> mixins,
            @Nullable UserDefinedContent content,
            Map<String, Module> modules,
            List<Module> globalModules,
            List<Module> forwardedModules) {
        this.variables = variables;
        this.functions = functions;
        this.mixins = mixins;
        this.content = content;
        this.modules.putAll(modules);
        this.globalModules.addAll(globalModules);
        this.forwardedModules.addAll(forwardedModules);
    }

    /**
     * Creates a closure over the current environment.
     *
     * <p>The closure captures a snapshot of the current scope stack so that
     * callables defined in this environment can access their enclosing scopes
     * even after those scopes have been popped. Also captures module references
     * so that functions/mixins can access their module-scoped imports.</p>
     */
    public Environment closure() {
        return new Environment(
                copyStack(variables),
                copyStack(functions),
                copyStack(mixins),
                content,
                modules,
                globalModules,
                forwardedModules
        );
    }

    private static <T> List<Map<String, T>> copyStack(List<Map<String, T>> stack) {
        var copy = new ArrayList<Map<String, T>>(stack.size());
        for (var map : stack) {
            copy.add(new HashMap<>(map));
        }
        return copy;
    }

    // -----------------------------------------------------------------------
    // Scope management
    // -----------------------------------------------------------------------

    /**
     * Runs {@code callback} in a new scope, then pops the scope.
     *
     * @param callback the code to run in the new scope
     * @param <T> the return type
     * @return the value returned by the callback
     */
    public <T> T scope(Supplier<T> callback) {
        variables.add(new HashMap<>());
        functions.add(new HashMap<>());
        mixins.add(new HashMap<>());
        try {
            return callback.get();
        } finally {
            variables.remove(variables.size() - 1);
            functions.remove(functions.size() - 1);
            mixins.remove(mixins.size() - 1);
        }
    }

    /**
     * Runs {@code callback} in a new scope, then pops the scope.
     * Void version for statements that don't return a value.
     */
    public void scope(Runnable callback) {
        variables.add(new HashMap<>());
        functions.add(new HashMap<>());
        mixins.add(new HashMap<>());
        try {
            callback.run();
        } finally {
            variables.remove(variables.size() - 1);
            functions.remove(functions.size() - 1);
            mixins.remove(mixins.size() - 1);
        }
    }

    // -----------------------------------------------------------------------
    // Variables
    // -----------------------------------------------------------------------

    /**
     * Returns the value of the variable named {@code name}, searching from
     * the innermost scope outward. Returns {@code null} if not found.
     */
    public @Nullable Value getVariable(String name) {
        for (int i = variables.size() - 1; i >= 0; i--) {
            var value = variables.get(i).get(name);
            if (value != null) return value;
        }
        return null;
    }

    /**
     * Sets a variable in the appropriate scope.
     *
     * <p>If the variable already exists in an enclosing scope, it is updated
     * there. Otherwise, it is set in the current (innermost) scope.</p>
     *
     * @param name the variable name (without {@code $})
     * @param value the value to set
     */
    public void setVariable(String name, Value value) {
        for (int i = variables.size() - 1; i >= 0; i--) {
            if (variables.get(i).containsKey(name)) {
                variables.get(i).put(name, value);
                return;
            }
        }
        // Not found in any scope: set in current scope
        variables.get(variables.size() - 1).put(name, value);
    }

    /**
     * Sets a variable in the current (innermost) scope, regardless of whether
     * it exists in an enclosing scope.
     */
    public void setLocalVariable(String name, Value value) {
        variables.get(variables.size() - 1).put(name, value);
    }

    /**
     * Sets a variable at the global (root) scope.
     */
    public void setGlobalVariable(String name, Value value) {
        variables.get(0).put(name, value);
    }

    /**
     * Returns the value of a global variable, or {@code null} if not found.
     */
    public @Nullable Value getGlobalVariable(String name) {
        return variables.get(0).get(name);
    }

    /** Returns whether a variable with the given name exists in any scope. */
    public boolean variableExists(String name) {
        return getVariable(name) != null;
    }

    /** Returns whether a variable exists at the global scope. */
    public boolean globalVariableExists(String name) {
        return variables.get(0).containsKey(name);
    }

    /** Returns whether we are at the global (root) scope level. */
    public boolean atRoot() {
        return variables.size() == 1;
    }

    // -----------------------------------------------------------------------
    // Functions
    // -----------------------------------------------------------------------

    /**
     * Returns the function named {@code name}, searching from the innermost
     * scope outward. Returns {@code null} if not found.
     */
    public @Nullable Callable getFunction(String name) {
        for (int i = functions.size() - 1; i >= 0; i--) {
            var fn = functions.get(i).get(name);
            if (fn != null) return fn;
        }
        return null;
    }

    /** Sets a function in the current (innermost) scope. */
    public void setFunction(String name, Callable callable) {
        functions.get(functions.size() - 1).put(name, callable);
    }

    /** Returns whether a function with the given name exists. */
    public boolean functionExists(String name) {
        return getFunction(name) != null;
    }

    // -----------------------------------------------------------------------
    // Mixins
    // -----------------------------------------------------------------------

    /**
     * Returns the mixin named {@code name}, searching from the innermost
     * scope outward. Returns {@code null} if not found.
     */
    public @Nullable Callable getMixin(String name) {
        for (int i = mixins.size() - 1; i >= 0; i--) {
            var mixin = mixins.get(i).get(name);
            if (mixin != null) return mixin;
        }
        return null;
    }

    /** Sets a mixin in the current (innermost) scope. */
    public void setMixin(String name, Callable callable) {
        mixins.get(mixins.size() - 1).put(name, callable);
    }

    /** Returns whether a mixin with the given name exists. */
    public boolean mixinExists(String name) {
        return getMixin(name) != null;
    }

    // -----------------------------------------------------------------------
    // Module system
    // -----------------------------------------------------------------------

    /**
     * Adds a module accessible via the given namespace.
     * Called by @use with a namespace.
     */
    public void addModule(Module module, @Nullable String namespace) {
        if (namespace != null) {
            if (modules.containsKey(namespace)) {
                throw new IllegalStateException(
                        "There's already a module with namespace \"" + namespace + "\".");
            }
            modules.put(namespace, module);
        } else {
            // @use without namespace — merge members into global scope
            globalModules.add(module);
        }
    }

    /**
     * Forwards a module's (filtered) members for downstream @use.
     * Called by @forward.
     */
    public void forwardModule(Module module) {
        forwardedModules.add(module);
    }

    /**
     * Returns the module with the given namespace, or null if not found.
     */
    public @Nullable Module getModule(String namespace) {
        return modules.get(namespace);
    }

    /**
     * Returns a variable from a namespaced module.
     */
    public @Nullable Value getVariable(String name, @Nullable String namespace) {
        if (namespace != null) {
            var module = modules.get(namespace);
            if (module == null) {
                throw new IllegalStateException(
                        "There is no module with the namespace \"" + namespace + "\".");
            }
            return module.getVariables().get(name);
        }
        // No namespace: search local scope, then global modules
        var local = getVariable(name);
        if (local != null) return local;
        // Search global (namespaceless) modules
        for (var mod : globalModules) {
            var val = mod.getVariables().get(name);
            if (val != null) return val;
        }
        return null;
    }

    /**
     * Returns a function from a namespaced module.
     */
    public @Nullable Callable getFunction(String name, @Nullable String namespace) {
        if (namespace != null) {
            var module = modules.get(namespace);
            if (module == null) {
                throw new IllegalStateException(
                        "There is no module with the namespace \"" + namespace + "\".");
            }
            return module.getFunctions().get(name);
        }
        // No namespace: search local scope first
        var local = getFunction(name);
        if (local != null) return local;
        for (var mod : globalModules) {
            var fn = mod.getFunctions().get(name);
            if (fn != null) return fn;
        }
        return null;
    }

    /**
     * Returns a mixin from a namespaced module.
     */
    public @Nullable Callable getMixin(String name, @Nullable String namespace) {
        if (namespace != null) {
            var module = modules.get(namespace);
            if (module == null) {
                throw new IllegalStateException(
                        "There is no module with the namespace \"" + namespace + "\".");
            }
            return module.getMixins().get(name);
        }
        var local = getMixin(name);
        if (local != null) return local;
        for (var mod : globalModules) {
            var mx = mod.getMixins().get(name);
            if (mx != null) return mx;
        }
        return null;
    }

    /** Returns the set of module namespaces (for debugging). */
    public java.util.Set<String> getModuleNamespaces() {
        return modules.keySet();
    }

    /** Returns all forwarded modules. */
    public List<Module> getForwardedModules() {
        return forwardedModules;
    }

    /** Returns an immutable snapshot of the global-scope variables. */
    public Map<String, Value> getGlobalVariables() {
        return new HashMap<>(variables.get(0));
    }

    /** Returns an immutable snapshot of the global-scope functions. */
    public Map<String, Callable> getGlobalFunctions() {
        return new HashMap<>(functions.get(0));
    }

    /** Returns an immutable snapshot of the global-scope mixins. */
    public Map<String, Callable> getGlobalMixins() {
        return new HashMap<>(mixins.get(0));
    }

    // -----------------------------------------------------------------------
    // Content
    // -----------------------------------------------------------------------

    /** Returns the current @content callable, or {@code null} if none. */
    public @Nullable UserDefinedContent getContent() {
        return content;
    }

    /** Sets the current @content callable. */
    public void setContent(@Nullable UserDefinedContent content) {
        this.content = content;
    }

    /**
     * Represents a user-defined @content block, capturing the content block's
     * callable declaration and the environment in which it was defined.
     */
    public record UserDefinedContent(
            com.sass.ast.sass.ContentBlock declaration,
            Environment environment
    ) {}
}
