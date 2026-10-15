package com.sevasync.exception;

/** Missing, wrong or expired credentials (HTTP 401). The message never says which part was wrong. */
public class AuthenticationException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 401;
    }

    @Override
    public String code() {
        return "UNAUTHENTICATED";
    }
}
