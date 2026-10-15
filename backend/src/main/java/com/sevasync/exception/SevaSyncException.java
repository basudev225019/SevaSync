package com.sevasync.exception;

/**
 * Root of every business-rule failure in SevaSync. It is a checked exception on purpose: callers have to decide
 * what an expected failure (expired food, full shelter, wrong password ...) means for them.
 *
 * <p>Each subclass overrides {@link #httpStatus()} and {@link #code()}, so the API layer maps any failure to an
 * HTTP response polymorphically instead of with an {@code instanceof} chain.
 */
public class SevaSyncException extends Exception {
    private static final long serialVersionUID = 1L;

    public SevaSyncException(String message) {
        super(message);
    }

    public SevaSyncException(String message, Throwable cause) {
        super(message, cause);
    }

    /** HTTP status the API answers with when this exception escapes a request handler. */
    public int httpStatus() {
        return 400;
    }

    /** Stable, machine-readable error code for clients. */
    public String code() {
        return "BAD_REQUEST";
    }
}
