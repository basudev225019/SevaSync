package com.sevasync.exception;

/**
 * Infrastructure failure (database or file system). Unchecked, because no business caller can recover from it;
 * the API turns it into a generic HTTP 500 without leaking details.
 */
public class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
