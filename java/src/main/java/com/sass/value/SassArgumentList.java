package com.sass.value;

import java.util.List;
import java.util.Map;

/**
 * A SassScript argument list, as used by variadic functions.
 * <p>
 * Extends SassList with keyword argument support.
 */
public final class SassArgumentList extends SassList {
    private final Map<String, Value> keywords;
    private boolean keywordsAccessed = false;

    public SassArgumentList(List<Value> contents, Map<String, Value> keywords, ListSeparator separator) {
        super(contents, separator);
        this.keywords = Map.copyOf(keywords);
    }

    /** The keyword arguments passed to this argument list. */
    public Map<String, Value> getKeywords() {
        keywordsAccessed = true;
        return keywords;
    }

    /** Whether the keywords have been accessed. Used for error checking. */
    public boolean wereKeywordsAccessed() { return keywordsAccessed; }
}
