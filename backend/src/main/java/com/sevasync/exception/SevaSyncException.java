package com.sevasync.exception;

/** Base class of all checked business exceptions in SevaSync. */
public class SevaSyncException extends Exception {
    private static final long serialVersionUID = 1L;

    public SevaSyncException(String message) {
        super(message);
    }

    public SevaSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
