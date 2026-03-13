package com.sass.value;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SassMapTest {

    @Test
    void createsMapWithContents() {
        var map = new SassMap(Map.of(
                new SassString("a", true), SassNumber.create(1),
                new SassString("b", true), SassNumber.create(2)));
        assertThat(map.getContents()).hasSize(2);
    }

    @Test
    void emptyMapHasUndecidedSeparator() {
        assertThat(SassMap.EMPTY.getSeparator()).isEqualTo(ListSeparator.UNDECIDED);
    }

    @Test
    void nonEmptyMapHasCommaSeparator() {
        var map = new SassMap(Map.of(new SassString("a"), SassNumber.create(1)));
        assertThat(map.getSeparator()).isEqualTo(ListSeparator.COMMA);
    }

    @Test
    void asListReturnsPairs() {
        var contents = new LinkedHashMap<Value, Value>();
        contents.put(new SassString("a"), SassNumber.create(1));
        contents.put(new SassString("b"), SassNumber.create(2));
        var map = new SassMap(contents);
        var list = map.asList();
        assertThat(list).hasSize(2);
        // Each element should be a space-separated pair
        var first = (SassList) list.get(0);
        assertThat(first.getSeparator()).isEqualTo(ListSeparator.SPACE);
        assertThat(first.asList()).hasSize(2);
    }

    @Test
    void assertMapReturnsThis() {
        var map = new SassMap(Map.of(new SassString("a"), SassNumber.create(1)));
        assertThat(map.assertMap()).isSameAs(map);
    }

    @Test
    void tryMapReturnsThis() {
        var map = SassMap.EMPTY;
        assertThat(map.tryMap()).isSameAs(map);
    }

    @Test
    void emptyMapEqualsEmptyList() {
        assertThat(SassMap.EMPTY).isEqualTo(SassList.EMPTY);
    }

    @Test
    void equalMapsAreEqual() {
        var a = new SassMap(Map.of(new SassString("x"), SassNumber.create(1)));
        var b = new SassMap(Map.of(new SassString("x"), SassNumber.create(1)));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentMapsAreNotEqual() {
        var a = new SassMap(Map.of(new SassString("x"), SassNumber.create(1)));
        var b = new SassMap(Map.of(new SassString("x"), SassNumber.create(2)));
        assertThat(a).isNotEqualTo(b);
    }
}
