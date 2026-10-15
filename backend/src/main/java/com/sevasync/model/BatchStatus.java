package com.sevasync.model;

/**
 * <pre>
 * AVAILABLE --(all servings matched)--> ALLOCATED --(every dispatch delivered)--> COMPLETED
 *     |  ^                                  |
 *     |  +----(dispatch cancelled)----------+
 *     +--> WITHDRAWN (by the donor)      any open batch --(window passed)--> EXPIRED
 * </pre>
 */
public enum BatchStatus {
    AVAILABLE, ALLOCATED, COMPLETED, EXPIRED, WITHDRAWN;

    public boolean isOpen() {
        return this == AVAILABLE || this == ALLOCATED;
    }
}
