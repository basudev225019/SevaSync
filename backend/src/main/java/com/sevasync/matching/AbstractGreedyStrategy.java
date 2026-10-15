package com.sevasync.matching;

import com.sevasync.model.FoodBatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Template Method for greedy matchers. The skeleton is fixed:
 * <ol>
 *   <li>put eligible batches into a {@link PriorityQueue} ordered by {@link #batchOrder()};</li>
 *   <li>for each batch, rank the recipients that still have room with a second {@link PriorityQueue}
 *       ({@link #recipientOrder}), skipping pairings {@link #isFeasible} rejects;</li>
 *   <li>pour the batch into recipients in that order, splitting it when one cannot take everything.</li>
 * </ol>
 * Subclasses only supply the two orderings and, optionally, a feasibility rule and a score.
 * Complexity: O(B log B + B * R log R) for B batches and R recipients.
 */
public abstract class AbstractGreedyStrategy implements MatchingStrategy {

    /** Order in which batches are served (hook). */
    protected abstract Comparator<FoodBatch> batchOrder();

    /** Order in which recipients are offered one particular batch (hook). */
    protected abstract Comparator<RecipientSlot> recipientOrder(FoodBatch batch, MatchContext ctx);

    /** Whether a pairing is allowed at all (hook; default: always). */
    protected boolean isFeasible(FoodBatch batch, RecipientSlot slot, MatchContext ctx) {
        return true;
    }

    /** Informational score stored with the allocation (hook; default 0). */
    protected double score(FoodBatch batch, RecipientSlot slot, MatchContext ctx) {
        return 0;
    }

    @Override
    public final List<Allocation> allocate(List<FoodBatch> batches, List<RecipientSlot> slots, MatchContext ctx) {
        PriorityQueue<FoodBatch> batchQueue = new PriorityQueue<>(batchOrder());
        for (FoodBatch b : batches) {
            if (b.getRemainingServings() > 0 && b.minutesRemaining(ctx.now()) >= ctx.minDispatchMinutes()) {
                batchQueue.add(b);
            }
        }
        List<RecipientSlot> open = new ArrayList<>();
        for (RecipientSlot s : slots) {
            if (s.remaining() > 0) {
                open.add(s.copy());
            }
        }

        List<Allocation> out = new ArrayList<>();
        while (!batchQueue.isEmpty() && !open.isEmpty()) {
            FoodBatch batch = batchQueue.poll();
            int left = batch.getRemainingServings();
            PriorityQueue<RecipientSlot> ranked = new PriorityQueue<>(recipientOrder(batch, ctx));
            for (RecipientSlot s : open) {
                if (s.remaining() > 0 && isFeasible(batch, s, ctx)) {
                    ranked.add(s);
                }
            }
            while (left > 0 && !ranked.isEmpty()) {
                RecipientSlot slot = ranked.poll();
                int give = Math.min(left, slot.remaining());
                double km = ctx.distanceKm(batch.getDonorId(), slot.recipient().getLocation());
                out.add(new Allocation(batch.getId(), slot.recipient().getId(), give, km, score(batch, slot, ctx)));
                slot.take(give);
                left -= give;
            }
            open.removeIf(s -> s.remaining() == 0);
        }
        return out;
    }

    @Override
    public String toString() {
        return id();
    }
}
