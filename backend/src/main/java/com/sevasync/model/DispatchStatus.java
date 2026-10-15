package com.sevasync.model;

/** Persisted name of each dispatch state. The behaviour of each state lives in {@link DispatchState}. */
public enum DispatchStatus {
    ASSIGNED, CLAIMED, PICKED_UP, DELIVERED, CANCELLED;

    /** The state object that implements this status's rules. */
    public DispatchState state() {
        return switch (this) {
            case ASSIGNED -> DispatchState.ASSIGNED;
            case CLAIMED -> DispatchState.CLAIMED;
            case PICKED_UP -> DispatchState.PICKED_UP;
            case DELIVERED -> DispatchState.DELIVERED;
            case CANCELLED -> DispatchState.CANCELLED;
        };
    }

    /** Food is still moving: matched but not yet handed over. */
    public boolean isActive() {
        return this == ASSIGNED || this == CLAIMED || this == PICKED_UP;
    }
}
