package com.sevasync.exception;

/** The request is valid but clashes with the current state, e.g. an e-mail that is already registered (HTTP 409). */
public class ConflictException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 409;
    }

    @Override
    public String code() {
        return "CONFLICT";
    }
}
