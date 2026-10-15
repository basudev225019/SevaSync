package com.sevasync.exception;

/** A life-cycle step that the current state does not allow, e.g. delivering a dispatch nobody picked up (HTTP 409). */
public class IllegalTransitionException extends ConflictException {
    private static final long serialVersionUID = 1L;

    public IllegalTransitionException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "ILLEGAL_TRANSITION";
    }
}
