package com.sass.value;

/**
 * An enum of list separator types.
 */
public enum ListSeparator {
    /** A space-separated list. */
    SPACE("space", " "),

    /** A comma-separated list. */
    COMMA("comma", ","),

    /** A slash-separated list. */
    SLASH("slash", "/"),

    /** A separator that hasn't yet been determined. */
    UNDECIDED("undecided", null);

    private final String name;
    private final String separator;

    ListSeparator(String name, String separator) {
        this.name = name;
        this.separator = separator;
    }

    /** The separator character, or null if undecided. */
    public String getSeparator() { return separator; }

    @Override
    public String toString() { return name; }
}
