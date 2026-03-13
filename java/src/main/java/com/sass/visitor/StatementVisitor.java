package com.sass.visitor;

import com.sass.ast.sass.*;

/**
 * Visitor interface for Sass statement AST nodes.
 *
 * @param <T> the return type of each visit method
 */
public interface StatementVisitor<T> {
    T visitAtRootRule(AtRootRule node);
    T visitAtRule(AtRule node);
    T visitContentBlock(ContentBlock node);
    T visitContentRule(ContentRule node);
    T visitDebugRule(DebugRule node);
    T visitDeclaration(Declaration node);
    T visitEachRule(EachRule node);
    T visitErrorRule(ErrorRule node);
    T visitExtendRule(ExtendRule node);
    T visitForRule(ForRule node);
    T visitForwardRule(ForwardRule node);
    T visitFunctionRule(FunctionRule node);
    T visitIfRule(IfRule node);
    T visitImportRule(ImportRule node);
    T visitIncludeRule(IncludeRule node);
    T visitLoudComment(LoudComment node);
    T visitMediaRule(MediaRule node);
    T visitMixinRule(MixinRule node);
    T visitReturnRule(ReturnRule node);
    T visitSilentComment(SilentComment node);
    T visitStyleRule(StyleRule node);
    T visitStylesheet(Stylesheet node);
    T visitSupportsRule(SupportsRule node);
    T visitUseRule(UseRule node);
    T visitVariableDeclaration(VariableDeclaration node);
    T visitWarnRule(WarnRule node);
    T visitWhileRule(WhileRule node);
}
