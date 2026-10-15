package com.sevasync.matching;

/**
 * One decision of a matching strategy: send {@code servings} of a batch to a recipient.
 *
 * @param distanceKm donor-to-recipient distance
 * @param score      the strategy's preference for this pairing (higher is better; informational)
 */
public record Allocation(String batchId, String recipientId, int servings, double distanceKm, double score) {
    public Allocation {
        if (servings <= 0) {
            throw new IllegalArgumentException("servings must be positive");
        }
    }
}
