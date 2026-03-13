package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;

import java.util.List;

/**
 * An {@code @import} rule.
 */
public final class ImportRule extends Statement {

    private final List<Import> imports;
    private final FileSpan span;

    public ImportRule(List<Import> imports, FileSpan span) {
        this.imports = List.copyOf(imports);
        this.span = span;
    }

    public List<Import> getImports() {
        return imports;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitImportRule(this);
    }
}
