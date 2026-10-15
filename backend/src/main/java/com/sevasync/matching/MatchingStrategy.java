package com.sevasync.matching;

import com.sevasync.model.FoodBatch;

import java.util.List;

/**
 * Strategy pattern: one interchangeable algorithm that decides which recipient gets which food.
 * Implementations must be pure - no I/O, no mutation of the inputs - so the same strategy can drive a live run,
 * a dry-run preview and the offline benchmark.
 */
public interface MatchingStrategy {

    /** Stable identifier used in settings and the API, e.g. {@code "smart"}. */
    String id();

    String displayName();

    String description();

    /**
     * @param batches open batches with servings left (in no particular order)
     * @param slots   recipients that can still take food today
     */
    List<Allocation> allocate(List<FoodBatch> batches, List<RecipientSlot> slots, MatchContext context);
}
