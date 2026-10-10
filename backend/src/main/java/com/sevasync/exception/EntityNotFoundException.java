package com.sevasync.exception;

/** Raised when a donor, recipient, batch or dispatch id does not exist. */
public class EntityNotFoundException extends SevaSyncException {
    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String type, String id) {
        super(type + " not found: " + id);
    }
}
