package com.sevasync.exception;

/** Raised when a batch or a recipient cannot absorb the requested number of servings. */
public class CapacityExceededException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public CapacityExceededException(String message) {
        super(message);
    }
}
