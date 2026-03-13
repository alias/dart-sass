package com.sass.visitor;

import com.sass.ast.css.CssNode;

/**
 * Visitor interface for CSS AST nodes.
 *
 * <p>Each visit method receives the node as a {@link CssNode}. Visitor
 * implementations can cast to the specific type if needed. This design
 * allows both immutable CSS nodes and their modifiable counterparts
 * (which share the same {@code accept} method names) to be visited
 * through a single visitor interface.</p>
 *
 * @param <T> the return type of each visit method
 */
public interface CssVisitor<T> {
    T visitCssAtRule(CssNode node);
    T visitCssComment(CssNode node);
    T visitCssDeclaration(CssNode node);
    T visitCssImport(CssNode node);
    T visitCssKeyframeBlock(CssNode node);
    T visitCssMediaRule(CssNode node);
    T visitCssStyleRule(CssNode node);
    T visitCssStylesheet(CssNode node);
    T visitCssSupportsRule(CssNode node);
}
