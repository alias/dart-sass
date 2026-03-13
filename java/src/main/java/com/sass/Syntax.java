package com.sass;

/**
 * An enum of syntaxes that Sass can parse.
 */
public enum Syntax {
    /** The CSS-superset SCSS syntax. */
    SCSS,

    /** The whitespace-sensitive indented syntax. */
    SASS,

    /** Plain CSS syntax that disallows all special Sass features. */
    CSS
}
