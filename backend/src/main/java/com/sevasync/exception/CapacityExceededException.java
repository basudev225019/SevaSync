package com.sevasync.exception;

/** A batch, recipient or vehicle cannot take the requested number of servings (HTTP 409). */
public class CapacityExceededException extends ConflictException {
    private static final long serialVersionUID = 1L;

    public CapacityExceededException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "CAPACITY_EXCEEDED";
    }
}
