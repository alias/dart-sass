package com.sass.visitor;

import com.sass.ast.selector.*;

/**
 * Visitor interface for selector AST nodes.
 *
 * @param <T> the return type of each visit method
 */
public interface SelectorVisitor<T> {
    T visitAttributeSelector(AttributeSelector node);
    T visitClassSelector(ClassSelector node);
    T visitComplexSelector(ComplexSelector node);
    T visitCompoundSelector(CompoundSelector node);
    T visitIDSelector(IDSelector node);
    T visitParentSelector(ParentSelector node);
    T visitPlaceholderSelector(PlaceholderSelector node);
    T visitPseudoSelector(PseudoSelector node);
    T visitSelectorList(SelectorList node);
    T visitTypeSelector(TypeSelector node);
    T visitUniversalSelector(UniversalSelector node);
}
