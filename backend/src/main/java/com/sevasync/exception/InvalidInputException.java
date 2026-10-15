package com.sevasync.exception;

/** Malformed or out-of-range input (HTTP 400). */
public class InvalidInputException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_INPUT";
    }
}
