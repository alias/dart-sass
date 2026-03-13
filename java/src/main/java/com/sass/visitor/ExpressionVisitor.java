package com.sass.visitor;

import com.sass.ast.sass.BinaryOperationExpression;
import com.sass.ast.sass.BooleanExpression;
import com.sass.ast.sass.ColorExpression;
import com.sass.ast.sass.FunctionExpression;
import com.sass.ast.sass.IfExpression;
import com.sass.ast.sass.InterpolatedFunctionExpression;
import com.sass.ast.sass.ListExpression;
import com.sass.ast.sass.MapExpression;
import com.sass.ast.sass.NullExpression;
import com.sass.ast.sass.NumberExpression;
import com.sass.ast.sass.ParenthesizedExpression;
import com.sass.ast.sass.SelectorExpression;
import com.sass.ast.sass.StringExpression;
import com.sass.ast.sass.SupportsExpression;
import com.sass.ast.sass.UnaryOperationExpression;
import com.sass.ast.sass.ValueExpression;
import com.sass.ast.sass.VariableExpression;

/**
 * Visitor interface for Sass expression AST nodes.
 *
 * @param <T> the return type of each visit method
 */
public interface ExpressionVisitor<T> {
    T visitBinaryOperationExpression(BinaryOperationExpression node);
    T visitBooleanExpression(BooleanExpression node);
    T visitColorExpression(ColorExpression node);
    T visitFunctionExpression(FunctionExpression node);
    T visitIfExpression(IfExpression node);
    T visitInterpolatedFunctionExpression(InterpolatedFunctionExpression node);
    T visitListExpression(ListExpression node);
    T visitMapExpression(MapExpression node);
    T visitNullExpression(NullExpression node);
    T visitNumberExpression(NumberExpression node);
    T visitParenthesizedExpression(ParenthesizedExpression node);
    T visitSelectorExpression(SelectorExpression node);
    T visitStringExpression(StringExpression node);
    T visitSupportsExpression(SupportsExpression node);
    T visitUnaryOperationExpression(UnaryOperationExpression node);
    T visitValueExpression(ValueExpression node);
    T visitVariableExpression(VariableExpression node);
}
