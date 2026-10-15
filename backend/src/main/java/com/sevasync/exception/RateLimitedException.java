package com.sevasync.exception;

/** Too many attempts in a short time, e.g. repeated failed logins (HTTP 429). */
public class RateLimitedException extends SevaSyncException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public int httpStatus() {
        return 429;
    }

    @Override
    public String code() {
        return "RATE_LIMITED";
    }
}
