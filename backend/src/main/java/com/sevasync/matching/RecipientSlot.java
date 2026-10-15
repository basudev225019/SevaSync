package com.sevasync.matching;

import com.sevasync.model.Recipient;

/**
 * A recipient together with the servings it can still absorb today and what it received recently
 * (used for fairness). Mutable on purpose: strategies decrease {@link #remaining} as they allocate.
 */
public final class RecipientSlot {
    private final Recipient recipient;
    private int remaining;
    private final int recentServings;

    public RecipientSlot(Recipient recipient, int remaining, int recentServings) {
        this.recipient = recipient;
        this.remaining = remaining;
        this.recentServings = recentServings;
    }

    public Recipient recipient() {
        return recipient;
    }

    public int remaining() {
        return remaining;
    }

    public int recentServings() {
        return recentServings;
    }

    void take(int servings) {
        if (servings <= 0 || servings > remaining) {
            throw new IllegalArgumentException("Cannot take " + servings + " of " + remaining);
        }
        remaining -= servings;
    }

    /** Copy with the same numbers, so a strategy run never mutates the caller's slots. */
    RecipientSlot copy() {
        return new RecipientSlot(recipient, remaining, recentServings);
    }
}
