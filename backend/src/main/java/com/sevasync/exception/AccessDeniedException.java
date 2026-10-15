package com.sevasync.exception;

/** The caller is signed in but their role or account status does not allow the action (HTTP 403). */
public class AccessDeniedException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 403;
    }

    @Override
    public String code() {
        return "FORBIDDEN";
    }
}
