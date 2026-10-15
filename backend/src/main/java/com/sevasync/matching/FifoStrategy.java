package com.sevasync.matching;

import com.sevasync.model.FoodBatch;

import java.util.Comparator;

/**
 * Baseline: first logged, first served, to recipients in registration order. This is what a WhatsApp group or a
 * notice board effectively does, and it is the yardstick the smarter strategies are measured against.
 */
public class FifoStrategy extends AbstractGreedyStrategy {

    @Override
    public String id() {
        return "fifo";
    }

    @Override
    public String displayName() {
        return "First come, first served";
    }

    @Override
    public String description() {
        return "Batches in the order they were logged, recipients in registration order. The naive baseline.";
    }

    @Override
    protected Comparator<FoodBatch> batchOrder() {
        return Comparator.comparing(FoodBatch::getCreatedAt).thenComparing(FoodBatch::getId);
    }

    @Override
    protected Comparator<RecipientSlot> recipientOrder(FoodBatch batch, MatchContext ctx) {
        return Comparator.comparing(s -> s.recipient().getId());
    }
}
