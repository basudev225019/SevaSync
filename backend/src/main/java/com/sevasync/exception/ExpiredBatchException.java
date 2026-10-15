package com.sevasync.exception;

/** The food passed its safe consumption window (HTTP 422). */
public class ExpiredBatchException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public ExpiredBatchException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 422;
    }

    @Override
    public String code() {
        return "EXPIRED_BATCH";
    }
}
