package com.sass.value;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SassListTest {

    @Test
    void createsCommaSeparatedList() {
        var list = new SassList(
                List.of(SassNumber.create(1), SassNumber.create(2)),
                ListSeparator.COMMA);
        assertThat(list.asList()).hasSize(2);
        assertThat(list.getSeparator()).isEqualTo(ListSeparator.COMMA);
        assertThat(list.hasBrackets()).isFalse();
    }

    @Test
    void createsSpaceSeparatedList() {
        var list = new SassList(
                List.of(SassNumber.create(1), SassNumber.create(2)),
                ListSeparator.SPACE);
        assertThat(list.getSeparator()).isEqualTo(ListSeparator.SPACE);
    }

    @Test
    void createsBracketedList() {
        var list = new SassList(
                List.of(SassNumber.create(1)),
                ListSeparator.SPACE, true);
        assertThat(list.hasBrackets()).isTrue();
    }

    @Test
    void emptyListIsBlank() {
        assertThat(SassList.EMPTY.isBlank()).isTrue();
    }

    @Test
    void emptyBracketedListIsNotBlank() {
        var list = new SassList(List.of(), ListSeparator.UNDECIDED, true);
        assertThat(list.isBlank()).isFalse();
    }

    @Test
    void multiElementRequiresExplicitSeparator() {
        assertThatThrownBy(() -> new SassList(
                List.of(SassNumber.create(1), SassNumber.create(2)),
                ListSeparator.UNDECIDED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleElementCanHaveUndecidedSeparator() {
        var list = new SassList(List.of(SassNumber.create(1)), ListSeparator.UNDECIDED);
        assertThat(list.asList()).hasSize(1);
    }

    @Test
    void emptyListCanBeAssertedAsMap() {
        assertThat(SassList.EMPTY.assertMap()).isEqualTo(SassMap.EMPTY);
    }

    @Test
    void nonEmptyListCannotBeAssertedAsMap() {
        var list = new SassList(List.of(SassNumber.create(1)), ListSeparator.SPACE);
        assertThatThrownBy(() -> list.assertMap())
                .isInstanceOf(com.sass.exception.SassScriptException.class);
    }

    @Test
    void equality() {
        var a = new SassList(List.of(SassNumber.create(1), SassNumber.create(2)), ListSeparator.COMMA);
        var b = new SassList(List.of(SassNumber.create(1), SassNumber.create(2)), ListSeparator.COMMA);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void equalityDependsOnSeparator() {
        var comma = new SassList(List.of(SassNumber.create(1), SassNumber.create(2)), ListSeparator.COMMA);
        var space = new SassList(List.of(SassNumber.create(1), SassNumber.create(2)), ListSeparator.SPACE);
        assertThat(comma).isNotEqualTo(space);
    }

    @Test
    void emptyListEqualsEmptyMap() {
        assertThat(SassList.EMPTY).isEqualTo(SassMap.EMPTY);
    }

    @Test
    void sassIndexToListIndex() {
        var list = new SassList(
                List.of(new SassString("a", true), new SassString("b", true), new SassString("c", true)),
                ListSeparator.COMMA);
        // 1-based → 0-based
        assertThat(list.sassIndexToListIndex(SassNumber.create(1), null)).isEqualTo(0);
        assertThat(list.sassIndexToListIndex(SassNumber.create(3), null)).isEqualTo(2);
        // negative
        assertThat(list.sassIndexToListIndex(SassNumber.create(-1), null)).isEqualTo(2);
    }
}
