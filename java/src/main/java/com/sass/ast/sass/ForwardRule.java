package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A {@code @forward} rule.
 */
public final class ForwardRule extends Statement {

    private final String url;
    private final @Nullable Set<String> shownMixinsAndFunctions;
    private final @Nullable Set<String> shownVariables;
    private final @Nullable Set<String> hiddenMixinsAndFunctions;
    private final @Nullable Set<String> hiddenVariables;
    private final @Nullable String prefix;
    private final List<ConfiguredVariable> configuration;
    private final FileSpan span;

    public ForwardRule(
            String url,
            @Nullable Set<String> shownMixinsAndFunctions,
            @Nullable Set<String> shownVariables,
            @Nullable Set<String> hiddenMixinsAndFunctions,
            @Nullable Set<String> hiddenVariables,
            @Nullable String prefix,
            List<ConfiguredVariable> configuration,
            FileSpan span) {
        this.url = url;
        this.shownMixinsAndFunctions = shownMixinsAndFunctions != null
                ? Set.copyOf(shownMixinsAndFunctions) : null;
        this.shownVariables = shownVariables != null
                ? Set.copyOf(shownVariables) : null;
        this.hiddenMixinsAndFunctions = hiddenMixinsAndFunctions != null
                ? Set.copyOf(hiddenMixinsAndFunctions) : null;
        this.hiddenVariables = hiddenVariables != null
                ? Set.copyOf(hiddenVariables) : null;
        this.prefix = prefix;
        this.configuration = List.copyOf(configuration);
        this.span = span;
    }

    public String getUrl() {
        return url;
    }

    public @Nullable Set<String> getShownMixinsAndFunctions() {
        return shownMixinsAndFunctions;
    }

    public @Nullable Set<String> getShownVariables() {
        return shownVariables;
    }

    public @Nullable Set<String> getHiddenMixinsAndFunctions() {
        return hiddenMixinsAndFunctions;
    }

    public @Nullable Set<String> getHiddenVariables() {
        return hiddenVariables;
    }

    public @Nullable String getPrefix() {
        return prefix;
    }

    public List<ConfiguredVariable> getConfiguration() {
        return configuration;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitForwardRule(this);
    }
}
