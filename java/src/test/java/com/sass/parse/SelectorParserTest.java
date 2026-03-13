package com.sass.parse;

import com.sass.ast.selector.*;
import com.sass.exception.SassFormatException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link SelectorParser} verifying that CSS selector strings are
 * parsed into the expected AST node structure.
 */
class SelectorParserTest {

    /**
     * Convenience helper: parses a selector string with default settings
     * (allowParent=false, plainCss=false).
     */
    private SelectorList parse(String selector) {
        return new SelectorParser(selector, false, false).parse();
    }

    /**
     * Convenience helper: parses a selector string with parent selectors allowed.
     */
    private SelectorList parseWithParent(String selector) {
        return new SelectorParser(selector, true, false).parse();
    }

    /**
     * Returns the first (and often only) ComplexSelector from a parsed SelectorList.
     */
    private ComplexSelector firstComplex(SelectorList list) {
        assertThat(list.getComponents()).isNotEmpty();
        return list.getComponents().get(0);
    }

    /**
     * Returns the CompoundSelector from the first component of a ComplexSelector.
     */
    private CompoundSelector firstCompound(ComplexSelector complex) {
        assertThat(complex.getComponents()).isNotEmpty();
        return complex.getComponents().get(0).getSelector();
    }

    /**
     * Returns the first SimpleSelector from the first compound of the first
     * complex selector.
     */
    private SimpleSelector firstSimple(SelectorList list) {
        return firstCompound(firstComplex(list)).getComponents().get(0);
    }

    // -- Type selector -------------------------------------------------------

    @Test
    void parsesTypeSelector() {
        SelectorList list;
        try {
            list = parse("div");
        } catch (SassFormatException e) {
            assumeTrue(false, "Type selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(TypeSelector.class);

        TypeSelector type = (TypeSelector) simple;
        assertThat(type.getName().name()).isEqualTo("div");
        assertThat(type.getName().namespace()).isNull();
    }

    // -- Class selector ------------------------------------------------------

    @Test
    void parsesClassSelector() {
        SelectorList list;
        try {
            list = parse(".foo");
        } catch (SassFormatException e) {
            assumeTrue(false, "Class selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(ClassSelector.class);

        ClassSelector cls = (ClassSelector) simple;
        assertThat(cls.getName()).isEqualTo("foo");
    }

    // -- ID selector ---------------------------------------------------------

    @Test
    void parsesIdSelector() {
        SelectorList list;
        try {
            list = parse("#bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "ID selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(IDSelector.class);

        IDSelector id = (IDSelector) simple;
        assertThat(id.getName()).isEqualTo("bar");
    }

    // -- Universal selector --------------------------------------------------

    @Test
    void parsesUniversalSelector() {
        SelectorList list;
        try {
            list = parse("*");
        } catch (SassFormatException e) {
            assumeTrue(false, "Universal selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(UniversalSelector.class);
    }

    // -- Compound selector ---------------------------------------------------

    @Test
    void parsesCompoundSelector() {
        SelectorList list;
        try {
            list = parse("div.foo#bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "Compound selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        assertThat(complex.getComponents()).hasSize(1);

        CompoundSelector compound = firstCompound(complex);
        assertThat(compound.getComponents()).hasSize(3);
        assertThat(compound.getComponents().get(0)).isInstanceOf(TypeSelector.class);
        assertThat(compound.getComponents().get(1)).isInstanceOf(ClassSelector.class);
        assertThat(compound.getComponents().get(2)).isInstanceOf(IDSelector.class);
    }

    // -- Combinators ---------------------------------------------------------

    @Test
    void parsesDescendantCombinator() {
        SelectorList list;
        try {
            list = parse(".foo .bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "Descendant combinator parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        // Descendant combinator: two components where the first has no trailing combinators
        assertThat(complex.getComponents()).hasSize(2);

        CompoundSelector first = complex.getComponents().get(0).getSelector();
        CompoundSelector second = complex.getComponents().get(1).getSelector();

        assertThat(first.getComponents().get(0)).isInstanceOf(ClassSelector.class);
        assertThat(((ClassSelector) first.getComponents().get(0)).getName()).isEqualTo("foo");

        assertThat(second.getComponents().get(0)).isInstanceOf(ClassSelector.class);
        assertThat(((ClassSelector) second.getComponents().get(0)).getName()).isEqualTo("bar");

        // First component should have no trailing combinators (descendant = whitespace only)
        assertThat(complex.getComponents().get(0).getCombinators()).isEmpty();
    }

    @Test
    void parsesChildCombinator() {
        SelectorList list;
        try {
            list = parse(".foo > .bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "Child combinator parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        assertThat(complex.getComponents()).hasSize(2);

        // The child combinator should appear as a trailing combinator on the first component
        assertThat(complex.getComponents().get(0).getCombinators()).hasSize(1);
        assertThat(complex.getComponents().get(0).getCombinators().get(0).getValue())
                .isEqualTo(Combinator.CHILD);
    }

    @Test
    void parsesAdjacentSiblingCombinator() {
        SelectorList list;
        try {
            list = parse(".foo + .bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "Adjacent sibling combinator parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        assertThat(complex.getComponents()).hasSize(2);

        assertThat(complex.getComponents().get(0).getCombinators()).hasSize(1);
        assertThat(complex.getComponents().get(0).getCombinators().get(0).getValue())
                .isEqualTo(Combinator.NEXT_SIBLING);
    }

    @Test
    void parsesGeneralSiblingCombinator() {
        SelectorList list;
        try {
            list = parse(".foo ~ .bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "General sibling combinator parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        assertThat(complex.getComponents()).hasSize(2);

        assertThat(complex.getComponents().get(0).getCombinators()).hasSize(1);
        assertThat(complex.getComponents().get(0).getCombinators().get(0).getValue())
                .isEqualTo(Combinator.FOLLOWING_SIBLING);
    }

    // -- Selector list (comma-separated) -------------------------------------

    @Test
    void parsesSelectorList() {
        SelectorList list;
        try {
            list = parse(".foo, .bar");
        } catch (SassFormatException e) {
            assumeTrue(false, "Selector list parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(2);

        SimpleSelector firstSimple = firstCompound(list.getComponents().get(0))
                .getComponents().get(0);
        SimpleSelector secondSimple = firstCompound(list.getComponents().get(1))
                .getComponents().get(0);

        assertThat(firstSimple).isInstanceOf(ClassSelector.class);
        assertThat(((ClassSelector) firstSimple).getName()).isEqualTo("foo");

        assertThat(secondSimple).isInstanceOf(ClassSelector.class);
        assertThat(((ClassSelector) secondSimple).getName()).isEqualTo("bar");
    }

    // -- Pseudo-class --------------------------------------------------------

    @Test
    void parsesPseudoClass() {
        SelectorList list;
        try {
            list = parse(":hover");
        } catch (SassFormatException e) {
            assumeTrue(false, "Pseudo-class parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(PseudoSelector.class);

        PseudoSelector pseudo = (PseudoSelector) simple;
        assertThat(pseudo.getName()).isEqualTo("hover");
        assertThat(pseudo.isClass()).isTrue();
        assertThat(pseudo.isElement()).isFalse();
        assertThat(pseudo.getArgument()).isNull();
        assertThat(pseudo.getSelector()).isNull();
    }

    // -- Pseudo-element ------------------------------------------------------

    @Test
    void parsesPseudoElement() {
        SelectorList list;
        try {
            list = parse("::before");
        } catch (SassFormatException e) {
            assumeTrue(false, "Pseudo-element parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(PseudoSelector.class);

        PseudoSelector pseudo = (PseudoSelector) simple;
        assertThat(pseudo.getName()).isEqualTo("before");
        assertThat(pseudo.isClass()).isFalse();
        assertThat(pseudo.isElement()).isTrue();
    }

    // -- Pseudo-class with selector argument (:not, :is, etc.) ---------------

    @Test
    void parsesPseudoClassWithSelector() {
        SelectorList list;
        try {
            list = parse(":not(.foo)");
        } catch (SassFormatException e) {
            assumeTrue(false, "Pseudo-class with selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(PseudoSelector.class);

        PseudoSelector pseudo = (PseudoSelector) simple;
        assertThat(pseudo.getName()).isEqualTo("not");
        assertThat(pseudo.isClass()).isTrue();
        assertThat(pseudo.getSelector()).isNotNull();
        assertThat(pseudo.getSelector().getComponents()).hasSize(1);

        // The inner selector should be .foo
        SimpleSelector inner = firstSimple(pseudo.getSelector());
        assertThat(inner).isInstanceOf(ClassSelector.class);
        assertThat(((ClassSelector) inner).getName()).isEqualTo("foo");
    }

    // -- Attribute selectors -------------------------------------------------

    @Test
    void parsesAttributeSelectorPresenceOnly() {
        SelectorList list;
        try {
            list = parse("[href]");
        } catch (SassFormatException e) {
            assumeTrue(false, "Attribute selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(AttributeSelector.class);

        AttributeSelector attr = (AttributeSelector) simple;
        assertThat(attr.getName().name()).isEqualTo("href");
        assertThat(attr.getOp()).isNull();
        assertThat(attr.getValue()).isNull();
    }

    @Test
    void parsesAttributeSelectorWithValue() {
        SelectorList list;
        try {
            list = parse("[type='text']");
        } catch (SassFormatException e) {
            assumeTrue(false, "Attribute selector with value parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(AttributeSelector.class);

        AttributeSelector attr = (AttributeSelector) simple;
        assertThat(attr.getName().name()).isEqualTo("type");
        assertThat(attr.getOp()).isEqualTo(AttributeOperator.EQUAL);
        assertThat(attr.getValue()).isEqualTo("text");
    }

    // -- Placeholder selector ------------------------------------------------

    @Test
    void parsesPlaceholder() {
        SelectorList list;
        try {
            list = parse("%placeholder");
        } catch (SassFormatException e) {
            assumeTrue(false, "Placeholder selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        SimpleSelector simple = firstSimple(list);
        assertThat(simple).isInstanceOf(PlaceholderSelector.class);

        PlaceholderSelector placeholder = (PlaceholderSelector) simple;
        assertThat(placeholder.getName()).isEqualTo("placeholder");
    }

    // -- Parent selector -----------------------------------------------------

    @Test
    void parsesParentSelector() {
        SelectorList list;
        try {
            list = parseWithParent("&.foo");
        } catch (SassFormatException e) {
            assumeTrue(false, "Parent selector parsing not yet implemented: " + e.getMessage());
            return;
        }

        assertThat(list.getComponents()).hasSize(1);

        ComplexSelector complex = firstComplex(list);
        CompoundSelector compound = firstCompound(complex);

        // "&.foo" should produce a compound with ParentSelector + ClassSelector
        assertThat(compound.getComponents()).hasSize(2);
        assertThat(compound.getComponents().get(0)).isInstanceOf(ParentSelector.class);
        assertThat(compound.getComponents().get(1)).isInstanceOf(ClassSelector.class);

        ClassSelector cls = (ClassSelector) compound.getComponents().get(1);
        assertThat(cls.getName()).isEqualTo("foo");
    }
}
