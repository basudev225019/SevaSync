package com.sevasync.exception;

/** Raised when user supplied data fails validation or sanitisation. */
public class InvalidInputException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}
