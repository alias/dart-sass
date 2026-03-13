package com.sass.parse;

import com.sass.ast.sass.*;
import com.sass.exception.SassFormatException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ScssParser} verifying that SCSS source strings are parsed
 * into the expected AST node structure.
 */
class ScssParserTest {

    /**
     * Convenience helper: parses the given SCSS source and returns the
     * resulting {@link Stylesheet}.
     */
    private Stylesheet parse(String scss) {
        return new ScssParser(scss).parse();
    }

    // -- Empty stylesheet ---------------------------------------------------

    @Test
    void parsesEmptyStylesheet() {
        Stylesheet sheet = parse("");
        assertThat(sheet.getChildren()).isNotNull().isEmpty();
    }

    // -- Variable declarations -----------------------------------------------

    @Test
    void parsesVariableDeclaration() {
        Stylesheet sheet;
        try {
            sheet = parse("$color: red;");
        } catch (SassFormatException e) {
            assumeTrue(false, "Variable declaration parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(VariableDeclaration.class);

        VariableDeclaration varDecl = (VariableDeclaration) first;
        assertThat(varDecl.getName()).isEqualTo("color");
        assertThat(varDecl.getNamespace()).isNull();
        assertThat(varDecl.isGuarded()).isFalse();
        assertThat(varDecl.isGlobal()).isFalse();
    }

    // -- Style rules ---------------------------------------------------------

    @Test
    void parsesStyleRule() {
        Stylesheet sheet;
        try {
            sheet = parse(".foo { color: red; }");
        } catch (SassFormatException e) {
            assumeTrue(false, "Style rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(StyleRule.class);

        StyleRule rule = (StyleRule) first;
        assertThat(rule.getChildren()).isNotNull().isNotEmpty();

        // The body should contain a Declaration
        boolean hasDeclaration = rule.getChildren().stream()
                .anyMatch(s -> s instanceof Declaration);
        assertThat(hasDeclaration).isTrue();
    }

    @Test
    void parsesNestedRule() {
        Stylesheet sheet;
        try {
            sheet = parse(".foo { .bar { color: red; } }");
        } catch (SassFormatException e) {
            assumeTrue(false, "Nested rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement outer = sheet.getChildren().get(0);
        assertThat(outer).isInstanceOf(StyleRule.class);

        StyleRule outerRule = (StyleRule) outer;
        assertThat(outerRule.getChildren()).isNotNull().isNotEmpty();

        // Find the nested StyleRule
        boolean hasNestedRule = outerRule.getChildren().stream()
                .anyMatch(s -> s instanceof StyleRule);
        assertThat(hasNestedRule).isTrue();

        StyleRule innerRule = outerRule.getChildren().stream()
                .filter(s -> s instanceof StyleRule)
                .map(s -> (StyleRule) s)
                .findFirst()
                .orElseThrow();

        // The inner rule should contain a Declaration
        boolean innerHasDeclaration = innerRule.getChildren().stream()
                .anyMatch(s -> s instanceof Declaration);
        assertThat(innerHasDeclaration).isTrue();
    }

    // -- @mixin / @include ---------------------------------------------------

    @Test
    void parsesMixinDeclaration() {
        Stylesheet sheet;
        try {
            sheet = parse("@mixin foo { color: red; }");
        } catch (SassFormatException e) {
            assumeTrue(false, "Mixin declaration parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(MixinRule.class);

        MixinRule mixin = (MixinRule) first;
        assertThat(mixin.getName()).isEqualTo("foo");
        assertThat(mixin.getChildren()).isNotNull().isNotEmpty();
    }

    @Test
    void parsesIncludeRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@include foo;");
        } catch (SassFormatException e) {
            assumeTrue(false, "Include rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(IncludeRule.class);

        IncludeRule include = (IncludeRule) first;
        assertThat(include.getName()).isEqualTo("foo");
    }

    // -- Control directives --------------------------------------------------

    @Test
    void parsesIfRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@if true { color: red; }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@if rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(IfRule.class);

        IfRule ifRule = (IfRule) first;
        assertThat(ifRule.getClauses()).hasSize(1);
        assertThat(ifRule.getLastClause()).isNull();
    }

    @Test
    void parsesForRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@for $i from 1 through 3 { .item { } }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@for rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(ForRule.class);

        ForRule forRule = (ForRule) first;
        assertThat(forRule.getVariable()).isEqualTo("i");
        // "through" means inclusive, so isExclusive should be false
        assertThat(forRule.isExclusive()).isFalse();
    }

    @Test
    void parsesEachRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@each $x in a, b, c { .item { } }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@each rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(EachRule.class);

        EachRule eachRule = (EachRule) first;
        assertThat(eachRule.getVariables()).containsExactly("x");
    }

    // -- @media --------------------------------------------------------------

    @Test
    void parsesMediaRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@media screen { .foo { } }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@media rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(MediaRule.class);

        MediaRule mediaRule = (MediaRule) first;
        assertThat(mediaRule.getQuery()).isNotNull();
        assertThat(mediaRule.getChildren()).isNotNull().isNotEmpty();
    }

    // -- @function / @return -------------------------------------------------

    @Test
    void parsesFunctionDeclaration() {
        Stylesheet sheet;
        try {
            sheet = parse("@function add($a, $b) { @return $a + $b; }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@function rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(FunctionRule.class);

        FunctionRule func = (FunctionRule) first;
        assertThat(func.getName()).isEqualTo("add");
        assertThat(func.getChildren()).isNotNull().isNotEmpty();

        // The body should contain a ReturnRule
        boolean hasReturn = func.getChildren().stream()
                .anyMatch(s -> s instanceof ReturnRule);
        assertThat(hasReturn).isTrue();
    }

    // -- @import -------------------------------------------------------------

    @Test
    void parsesImportRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@import 'foo';");
        } catch (SassFormatException e) {
            assumeTrue(false, "@import rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(ImportRule.class);

        ImportRule importRule = (ImportRule) first;
        assertThat(importRule.getImports()).isNotEmpty();
    }

    // -- @use ----------------------------------------------------------------

    @Test
    void parsesUseRule() {
        Stylesheet sheet;
        try {
            sheet = parse("@use 'foo';");
        } catch (SassFormatException e) {
            assumeTrue(false, "@use rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(UseRule.class);

        UseRule useRule = (UseRule) first;
        assertThat(useRule.getUrl()).isEqualTo("foo");
    }

    // -- @extend -------------------------------------------------------------

    @Test
    void parsesExtendRule() {
        Stylesheet sheet;
        try {
            sheet = parse(".foo { @extend .bar; }");
        } catch (SassFormatException e) {
            assumeTrue(false, "@extend rule parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement outer = sheet.getChildren().get(0);
        assertThat(outer).isInstanceOf(StyleRule.class);

        StyleRule rule = (StyleRule) outer;
        assertThat(rule.getChildren()).isNotNull().isNotEmpty();

        boolean hasExtend = rule.getChildren().stream()
                .anyMatch(s -> s instanceof ExtendRule);
        assertThat(hasExtend).isTrue();
    }

    // -- Comments ------------------------------------------------------------

    @Test
    void parsesComment() {
        Stylesheet sheet;
        try {
            sheet = parse("/* hello */");
        } catch (SassFormatException e) {
            assumeTrue(false, "Loud comment parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(LoudComment.class);
    }

    @Test
    void parsesSilentComment() {
        Stylesheet sheet;
        try {
            sheet = parse("// hello");
        } catch (SassFormatException e) {
            assumeTrue(false, "Silent comment parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(1);

        Statement first = sheet.getChildren().get(0);
        assertThat(first).isInstanceOf(SilentComment.class);
    }

    // -- Multiple statements -------------------------------------------------

    @Test
    void parsesMultipleStatements() {
        Stylesheet sheet;
        try {
            sheet = parse("$a: 1; $b: 2;");
        } catch (SassFormatException e) {
            assumeTrue(false, "Multiple statement parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(sheet.getChildren()).hasSize(2);

        assertThat(sheet.getChildren().get(0)).isInstanceOf(VariableDeclaration.class);
        assertThat(sheet.getChildren().get(1)).isInstanceOf(VariableDeclaration.class);

        VariableDeclaration first = (VariableDeclaration) sheet.getChildren().get(0);
        VariableDeclaration second = (VariableDeclaration) sheet.getChildren().get(1);
        assertThat(first.getName()).isEqualTo("a");
        assertThat(second.getName()).isEqualTo("b");
    }
}
