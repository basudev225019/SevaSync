package com.sevasync.exception;

/** The batch violates a food-safety rule, e.g. a declared shelf life longer than its category allows (HTTP 422). */
public class UnsafeFoodException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public UnsafeFoodException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 422;
    }

    @Override
    public String code() {
        return "UNSAFE_FOOD";
    }
}
