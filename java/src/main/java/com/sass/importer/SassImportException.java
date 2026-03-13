package com.sass.importer;

/**
 * Thrown when an @import/@use cannot be resolved unambiguously.
 */
public class SassImportException extends RuntimeException {
    public SassImportException(String message) {
        super(message);
    }
}
