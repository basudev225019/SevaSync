package com.sevasync.exception;

/** Raised when a batch violates a food-safety rule (shelf-life too long, too little time left, ...). */
public class UnsafeFoodException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public UnsafeFoodException(String message) {
        super(message);
    }
}
