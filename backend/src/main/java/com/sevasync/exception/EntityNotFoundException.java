package com.sevasync.exception;

/** A referenced donor, batch, dispatch ... does not exist (HTTP 404). */
public class EntityNotFoundException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String entity, String id) {
        super(entity + " " + id + " not found");
    }

    @Override
    public int httpStatus() {
        return 404;
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}
