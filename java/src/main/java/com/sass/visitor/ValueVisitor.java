package com.sass.visitor;

import com.sass.value.*;

/**
 * Visitor interface for SassScript values.
 *
 * @param <T> the return type of each visit method
 */
public interface ValueVisitor<T> {
    T visitBoolean(SassBoolean value);
    T visitCalculation(SassCalculation value);
    T visitColor(SassColor value);
    T visitFunction(SassFunction value);
    T visitList(SassList value);
    T visitMap(SassMap value);
    T visitMixin(SassMixin value);
    T visitNull();
    T visitNumber(SassNumber value);
    T visitString(SassString value);
}
