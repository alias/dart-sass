package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@code @use} rule.
 */
public final class UseRule extends Statement {

    private final String url;
    private final @Nullable String namespace;
    private final List<ConfiguredVariable> configuration;
    private final FileSpan span;

    public UseRule(
            String url,
            @Nullable String namespace,
            List<ConfiguredVariable> configuration,
            FileSpan span) {
        this.url = url;
        this.namespace = namespace;
        this.configuration = List.copyOf(configuration);
        this.span = span;
    }

    public String getUrl() {
        return url;
    }

    public @Nullable String getNamespace() {
        return namespace;
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
        return visitor.visitUseRule(this);
    }
}
