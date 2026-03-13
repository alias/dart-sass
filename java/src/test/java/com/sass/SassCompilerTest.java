package com.sass;

import com.sass.exception.SassRuntimeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SassCompilerTest {

    private static String compile(String scss) {
        return SassCompiler.compile(scss).css().trim();
    }

    private static String compileCompressed(String scss) {
        return SassCompiler.compileCompressed(scss).css().trim();
    }

    @Nested
    class BasicSelectors {
        @Test
        void simpleRule() {
            assertThat(compile(".foo { color: red; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void nestedRule() {
            assertThat(compile(".parent { .child { color: blue; } }"))
                    .isEqualTo(".parent .child {\n  color: blue;\n}");
        }

        @Test
        void multipleRules() {
            assertThat(compile("a { color: red; } b { color: blue; }"))
                    .isEqualTo("a {\n  color: red;\n}\n\nb {\n  color: blue;\n}");
        }
    }

    @Nested
    class Variables {
        @Test
        void simpleVariable() {
            assertThat(compile("$color: red; .foo { color: $color; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void variableReassignment() {
            assertThat(compile("$x: 1; $x: 2; .foo { val: $x; }"))
                    .isEqualTo(".foo {\n  val: 2;\n}");
        }

        @Test
        void defaultVariable() {
            assertThat(compile("$x: 1; $x: 2 !default; .foo { val: $x; }"))
                    .isEqualTo(".foo {\n  val: 1;\n}");
        }

        @Test
        void undefinedVariableThrows() {
            assertThatThrownBy(() -> compile(".foo { color: $undefined; }"))
                    .isInstanceOf(SassRuntimeException.class)
                    .hasMessageContaining("Undefined variable");
        }
    }

    @Nested
    class Interpolation {
        @Test
        void selectorInterpolation() {
            assertThat(compile("$name: foo; .#{$name} { color: red; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void propertyNameInterpolation() {
            assertThat(compile("$prop: color; .foo { #{$prop}: red; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void valueInterpolation() {
            assertThat(compile("$val: red; .foo { color: #{$val}; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }
    }

    @Nested
    class Arithmetic {
        @Test
        void addition() {
            assertThat(compile(".foo { width: 10px + 5px; }"))
                    .isEqualTo(".foo {\n  width: 15px;\n}");
        }

        @Test
        void subtraction() {
            assertThat(compile(".foo { width: 10px - 3px; }"))
                    .isEqualTo(".foo {\n  width: 7px;\n}");
        }

        @Test
        void multiplication() {
            assertThat(compile(".foo { width: 10px * 2; }"))
                    .isEqualTo(".foo {\n  width: 20px;\n}");
        }
    }

    @Nested
    class ControlFlow {
        @Test
        void ifTrue() {
            assertThat(compile("$x: true; .foo { @if $x { color: red; } }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void ifFalse() {
            assertThat(compile("$x: false; .foo { @if $x { color: red; } @else { color: blue; } }"))
                    .isEqualTo(".foo {\n  color: blue;\n}");
        }

        @Test
        void forLoop() {
            assertThat(compile("@for $i from 1 through 3 { .item-#{$i} { width: $i * 10px; } }"))
                    .contains(".item-1")
                    .contains(".item-2")
                    .contains(".item-3");
        }

        @Test
        void eachLoop() {
            assertThat(compile("@each $c in red, green, blue { .#{$c} { color: $c; } }"))
                    .contains(".red")
                    .contains(".green")
                    .contains(".blue");
        }

        @Test
        void whileLoop() {
            assertThat(compile("$i: 3; @while $i > 0 { .item-#{$i} { x: $i; } $i: $i - 1; }"))
                    .contains(".item-3")
                    .contains(".item-2")
                    .contains(".item-1");
        }
    }

    @Nested
    class Mixins {
        @Test
        void simpleMixin() {
            assertThat(compile(
                    "@mixin red-text { color: red; } .foo { @include red-text; }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }

        @Test
        void mixinWithArgs() {
            assertThat(compile(
                    "@mixin size($w, $h) { width: $w; height: $h; } " +
                    ".box { @include size(100px, 50px); }"))
                    .isEqualTo(".box {\n  width: 100px;\n  height: 50px;\n}");
        }

        @Test
        void mixinWithDefaultArg() {
            assertThat(compile(
                    "@mixin btn($color: blue) { color: $color; } " +
                    ".a { @include btn; } .b { @include btn(red); }"))
                    .contains("color: blue")
                    .contains("color: red");
        }
    }

    @Nested
    class Functions {
        @Test
        void userDefinedFunction() {
            assertThat(compile(
                    "@function double($n) { @return $n * 2; } " +
                    ".foo { width: double(5px); }"))
                    .isEqualTo(".foo {\n  width: 10px;\n}");
        }

        @Test
        void builtInTypeOf() {
            assertThat(compile(".foo { val: type-of(42); }"))
                    .isEqualTo(".foo {\n  val: number;\n}");
        }

        @Test
        void ifFunction() {
            assertThat(compile("$x: true; .foo { color: if($x, red, blue); }"))
                    .isEqualTo(".foo {\n  color: red;\n}");
        }
    }

    @Nested
    class Comments {
        @Test
        void loudComment() {
            assertThat(compile("/* hello */ .foo { color: red; }"))
                    .contains("/* hello */");
        }

        @Test
        void silentCommentOmitted() {
            assertThat(compile("// silent\n.foo { color: red; }"))
                    .doesNotContain("silent");
        }
    }

    @Nested
    class CompressedOutput {
        @Test
        void compressed() {
            var result = compileCompressed(".foo { color: red; }");
            assertThat(result).doesNotContain("\n");
        }
    }
}
