package com.sass.ast.sass;

import com.sass.util.FileSpan;
import com.sass.visitor.StatementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An {@code @if} / {@code @else if} / {@code @else} rule.
 */
public final class IfRule extends Statement {

    private final List<IfClause> clauses;
    private final @Nullable ElseClause lastClause;
    private final FileSpan span;

    public IfRule(List<IfClause> clauses, @Nullable ElseClause lastClause, FileSpan span) {
        this.clauses = List.copyOf(clauses);
        this.lastClause = lastClause;
        this.span = span;
    }

    public List<IfClause> getClauses() {
        return clauses;
    }

    public @Nullable ElseClause getLastClause() {
        return lastClause;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    @Override
    public <T> T accept(StatementVisitor<T> visitor) {
        return visitor.visitIfRule(this);
    }

    /** An {@code @if} or {@code @else if} clause with a condition. */
    public static final class IfClause {

        private final Expression condition;
        private final List<Statement> children;

        public IfClause(Expression condition, List<Statement> children) {
            this.condition = condition;
            this.children = List.copyOf(children);
        }

        public Expression getCondition() {
            return condition;
        }

        public List<Statement> getChildren() {
            return children;
        }
    }

    /** An {@code @else} clause with no condition. */
    public static final class ElseClause {

        private final List<Statement> children;

        public ElseClause(List<Statement> children) {
            this.children = List.copyOf(children);
        }

        public List<Statement> getChildren() {
            return children;
        }
    }
}
