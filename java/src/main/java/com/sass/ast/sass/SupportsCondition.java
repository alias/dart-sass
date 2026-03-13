package com.sass.ast.sass;

import com.sass.ast.SassNode;
import com.sass.util.FileSpan;

/**
 * An abstract interface for defining the condition a {@code @supports} rule selects.
 *
 * <p>Port of {@code lib/src/ast/sass/supports_condition.dart}.
 */
public interface SupportsCondition extends SassNode {

    /**
     * Returns a copy of this condition with the given {@code span} as its span.
     */
    SupportsCondition withSpan(FileSpan span);
}
