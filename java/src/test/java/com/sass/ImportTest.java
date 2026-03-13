package com.sass;

import com.sass.exception.SassRuntimeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests for @import, @use, and @forward with real
 * file I/O via {@code @TempDir}.
 */
class ImportTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // @import
    // -----------------------------------------------------------------------

    @Nested
    class ImportRule {

        @Test
        void basic_import_shares_variables() throws IOException {
            Files.writeString(tempDir.resolve("_variables.scss"),
                    "$primary: blue;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'variables';\n.btn { color: $primary; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains(".btn");
            assertThat(result.css()).contains("color: blue");
        }

        @Test
        void import_shares_mixins() throws IOException {
            Files.writeString(tempDir.resolve("_mixins.scss"),
                    "@mixin flex { display: flex; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'mixins';\n.container { @include flex; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("display: flex");
        }

        @Test
        void import_shares_functions() throws IOException {
            Files.writeString(tempDir.resolve("_functions.scss"),
                    "@function double($n) { @return $n * 2; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'functions';\n.box { width: double(10px); }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("width: 20px");
        }

        @Test
        void nested_transitive_imports() throws IOException {
            Files.writeString(tempDir.resolve("_colors.scss"),
                    "$red: #ff0000;");
            Files.writeString(tempDir.resolve("_theme.scss"),
                    "@import 'colors';\n$bg: $red;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'theme';\nbody { background: $bg; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("background: red");
        }

        @Test
        void circular_import_throws() throws IOException {
            Files.writeString(tempDir.resolve("a.scss"), "@import 'b';");
            Files.writeString(tempDir.resolve("b.scss"), "@import 'a';");

            assertThatThrownBy(() -> SassCompiler.compileFile(tempDir.resolve("a.scss")))
                    .isInstanceOf(SassRuntimeException.class)
                    .hasMessageContaining("already being loaded");
        }

        @Test
        void css_import_passed_through() throws IOException {
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'https://fonts.googleapis.com/css?family=Roboto';\n.a { color: red; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            // CSS/URL imports are emitted as-is
            assertThat(result.css()).contains("@import");
            assertThat(result.css()).contains("fonts.googleapis.com");
        }

        @Test
        void import_with_partial_prefix() throws IOException {
            Files.writeString(tempDir.resolve("_helpers.scss"),
                    ".helper { display: block; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'helpers';");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains(".helper");
        }

        @Test
        void import_from_load_path() throws IOException {
            var vendorDir = tempDir.resolve("vendor");
            Files.createDirectory(vendorDir);
            Files.writeString(vendorDir.resolve("_reset.scss"),
                    "* { margin: 0; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'reset';\n.app { padding: 0; }");

            var options = new CompileOptions(List.of(vendorDir));
            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"), options);

            assertThat(result.css()).contains("margin: 0");
            assertThat(result.css()).contains("padding: 0");
        }

        @Test
        void import_with_directory_index() throws IOException {
            var dir = tempDir.resolve("components");
            Files.createDirectory(dir);
            Files.writeString(dir.resolve("_index.scss"),
                    ".component { display: flex; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'components';");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains(".component");
        }

        @Test
        void import_inlines_css_output() throws IOException {
            Files.writeString(tempDir.resolve("_header.scss"),
                    ".header { height: 60px; }");
            Files.writeString(tempDir.resolve("_footer.scss"),
                    ".footer { height: 40px; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@import 'header';\n@import 'footer';\n.main { flex: 1; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains(".header");
            assertThat(result.css()).contains(".footer");
            assertThat(result.css()).contains(".main");
        }
    }

    // -----------------------------------------------------------------------
    // @use
    // -----------------------------------------------------------------------

    @Nested
    class UseRule {

        @Test
        void use_with_default_namespace() throws IOException {
            Files.writeString(tempDir.resolve("_vars.scss"),
                    "$color: green;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'vars';\n.a { color: vars.$color; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("color: green");
        }

        @Test
        void use_with_custom_namespace() throws IOException {
            Files.writeString(tempDir.resolve("_theme.scss"),
                    "$bg: white;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'theme' as t;\n.page { background: t.$bg; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("background: white");
        }

        @Test
        void use_with_star_namespace() throws IOException {
            Files.writeString(tempDir.resolve("_globals.scss"),
                    "$font: sans-serif;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'globals' as *;\n.text { font-family: $font; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("font-family: sans-serif");
        }

        @Test
        void use_with_function() throws IOException {
            Files.writeString(tempDir.resolve("_math-utils.scss"),
                    "@function triple($n) { @return $n * 3; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'math-utils';\n.box { width: math-utils.triple(5px); }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("width: 15px");
        }

        @Test
        void use_with_mixin() throws IOException {
            Files.writeString(tempDir.resolve("_layout.scss"),
                    "@mixin center { display: flex; justify-content: center; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'layout';\n.wrap { @include layout.center; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("display: flex");
            assertThat(result.css()).contains("justify-content: center");
        }

        @Test
        void use_inlines_module_css() throws IOException {
            Files.writeString(tempDir.resolve("_base.scss"),
                    "$size: 16px;\nhtml { font-size: $size; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'base';\n.app { padding: base.$size; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            // Both the module's own CSS and the importing file's CSS should appear
            assertThat(result.css()).contains("font-size: 16px");
            assertThat(result.css()).contains("padding: 16px");
        }

        @Test
        void use_with_configuration() throws IOException {
            Files.writeString(tempDir.resolve("_config.scss"),
                    "$primary: blue !default;\n.btn { color: $primary; }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'config' with ($primary: red);");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("color: red");
        }

        @Test
        void use_module_loaded_only_once() throws IOException {
            // When a module is @use'd from multiple places, it should only
            // be executed once (its functions/variables are cached).
            Files.writeString(tempDir.resolve("_shared.scss"),
                    "$counter: 1;\n@function get-counter() { @return $counter; }");
            Files.writeString(tempDir.resolve("_a.scss"),
                    "@use 'shared';\n.a { content: shared.get-counter(); }");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'a';\n@use 'shared';\n.main { content: shared.get-counter(); }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            // Both should see counter = 1 (module executed once)
            assertThat(result.css()).contains("content: 1");
        }
    }

    // -----------------------------------------------------------------------
    // @forward
    // -----------------------------------------------------------------------

    @Nested
    class ForwardRule {

        @Test
        void forward_re_exports_variables() throws IOException {
            Files.writeString(tempDir.resolve("_colors.scss"),
                    "$red: #f00;\n$blue: #00f;");
            Files.writeString(tempDir.resolve("_theme.scss"),
                    "@forward 'colors';");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'theme';\n.a { color: theme.$red; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("color: red");
        }

        @Test
        void forward_re_exports_functions() throws IOException {
            Files.writeString(tempDir.resolve("_helpers.scss"),
                    "@function half($n) { @return $n / 2; }");
            Files.writeString(tempDir.resolve("_utils.scss"),
                    "@forward 'helpers';");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'utils';\n.a { width: utils.half(20px); }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("width: 10px");
        }

        @Test
        void forward_with_prefix() throws IOException {
            Files.writeString(tempDir.resolve("_sizes.scss"),
                    "$small: 8px;\n$large: 24px;");
            Files.writeString(tempDir.resolve("_design.scss"),
                    "@forward 'sizes' as size-*;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'design';\n.a { padding: design.$size-small; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("padding: 8px");
        }

        @Test
        void forward_with_show() throws IOException {
            Files.writeString(tempDir.resolve("_all.scss"),
                    "$a: 1;\n$b: 2;\n$c: 3;");
            Files.writeString(tempDir.resolve("_filtered.scss"),
                    "@forward 'all' show $a, $b;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'filtered';\n.x { content: filtered.$a; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("content: 1");
        }

        @Test
        void forward_with_hide() throws IOException {
            Files.writeString(tempDir.resolve("_all.scss"),
                    "$public: yes;\n$private: no;");
            Files.writeString(tempDir.resolve("_api.scss"),
                    "@forward 'all' hide $private;");
            Files.writeString(tempDir.resolve("main.scss"),
                    "@use 'api';\n.x { content: api.$public; }");

            var result = SassCompiler.compileFile(tempDir.resolve("main.scss"));

            assertThat(result.css()).contains("content: yes");
        }
    }

    // -----------------------------------------------------------------------
    // compileFile API
    // -----------------------------------------------------------------------

    @Nested
    class CompileFileApi {

        @Test
        void compileFile_with_default_options() throws IOException {
            Files.writeString(tempDir.resolve("simple.scss"),
                    ".hello { color: red; }");

            var result = SassCompiler.compileFile(tempDir.resolve("simple.scss"));

            assertThat(result.css()).contains(".hello");
            assertThat(result.css()).contains("color: red");
        }

        @Test
        void compileFile_with_compressed_style() throws IOException {
            Files.writeString(tempDir.resolve("simple.scss"),
                    ".hello { color: red; }");

            var options = new CompileOptions(List.of(), OutputStyle.COMPRESSED);
            var result = SassCompiler.compileFile(tempDir.resolve("simple.scss"), options);

            // Compressed output should not have unnecessary whitespace
            assertThat(result.css()).doesNotContain("  color:");
        }

        @Test
        void compile_string_with_options_and_load_paths() throws IOException {
            var libDir = tempDir.resolve("lib");
            Files.createDirectory(libDir);
            Files.writeString(libDir.resolve("_vars.scss"), "$x: 42px;");

            var options = new CompileOptions(List.of(libDir));
            var result = SassCompiler.compile("@import 'vars';\n.a { width: $x; }", options);

            assertThat(result.css()).contains("width: 42px");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int countOccurrences(String text, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}
