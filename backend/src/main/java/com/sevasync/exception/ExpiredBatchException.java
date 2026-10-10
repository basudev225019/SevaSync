package com.sevasync.exception;

/** Raised when a food batch is already past its consumption window. */
public class ExpiredBatchException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public ExpiredBatchException(String message) {
        super(message);
    }
}
