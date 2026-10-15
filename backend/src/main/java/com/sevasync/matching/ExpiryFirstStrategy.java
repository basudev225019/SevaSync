package com.sevasync.matching;

import com.sevasync.model.FoodBatch;

import java.util.Comparator;

/**
 * The original SevaSync engine: soonest-expiring food first, to the neediest recipient first (then the one with the
 * most room left). Ignores distance.
 */
public class ExpiryFirstStrategy extends AbstractGreedyStrategy {

    @Override
    public String id() {
        return "expiry-first";
    }

    @Override
    public String displayName() {
        return "Expiry first, need first";
    }

    @Override
    public String description() {
        return "Dual priority queue: earliest expiry first, matched to the highest need level, then most free capacity.";
    }

    @Override
    protected Comparator<FoodBatch> batchOrder() {
        return Comparator.comparing(FoodBatch::getExpiresAt)
                .thenComparing(Comparator.comparingInt(FoodBatch::getRemainingServings).reversed())
                .thenComparing(FoodBatch::getId);
    }

    @Override
    protected Comparator<RecipientSlot> recipientOrder(FoodBatch batch, MatchContext ctx) {
        return Comparator.<RecipientSlot>comparingInt(s -> s.recipient().getNeedLevel().getWeight()).reversed()
                .thenComparing(Comparator.comparingInt(RecipientSlot::remaining).reversed())
                .thenComparing(s -> s.recipient().getId());
    }

    @Override
    protected double score(FoodBatch batch, RecipientSlot slot, MatchContext ctx) {
        return slot.recipient().getNeedLevel().getWeight();
    }
}
