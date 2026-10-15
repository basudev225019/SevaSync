package com.sevasync.matching;

import com.sevasync.model.FoodBatch;

import java.util.Comparator;

/**
 * Urgency + distance + fairness. Batches still go soonest-expiry first, but each batch goes to the recipient with
 * the best <i>score</i>:
 *
 * <pre>
 *   score = W_NEED * needWeight
 *         - W_DISTANCE * km
 *         - W_FAIRNESS * min(servedLast7Days / dailyCapacity, 2)
 *         + W_FIT      * (recipient can take the whole batch ? 1 : 0)
 * </pre>
 *
 * and a pairing is only allowed when a volunteer can realistically get there before the food expires
 * (trip time plus a safety margin).
 */
public class SmartStrategy extends AbstractGreedyStrategy {
    static final double W_NEED = 10;
    static final double W_DISTANCE = 2.5;
    static final double W_FAIRNESS = 4;
    static final double W_FIT = 2;
    /** Minutes of slack required between estimated arrival and expiry. */
    static final int SAFETY_MARGIN_MINUTES = 15;
    /** Assumed distance a volunteer travels to reach the donor first. */
    static final double PICKUP_LEG_KM = 1.0;

    @Override
    public String id() {
        return "smart";
    }

    @Override
    public String displayName() {
        return "Urgency, distance and fairness";
    }

    @Override
    public String description() {
        return "Earliest expiry first; each batch goes to the best-scoring reachable recipient, balancing need, "
                + "travel distance and how much each recipient already received this week.";
    }

    @Override
    protected Comparator<FoodBatch> batchOrder() {
        return Comparator.comparing(FoodBatch::getExpiresAt).thenComparing(FoodBatch::getId);
    }

    @Override
    protected Comparator<RecipientSlot> recipientOrder(FoodBatch batch, MatchContext ctx) {
        return Comparator.<RecipientSlot>comparingDouble(s -> score(batch, s, ctx)).reversed()
                .thenComparing(s -> s.recipient().getId());
    }

    @Override
    protected boolean isFeasible(FoodBatch batch, RecipientSlot slot, MatchContext ctx) {
        double km = ctx.distanceKm(batch.getDonorId(), slot.recipient().getLocation());
        int trip = ctx.referenceVehicle().tripMinutes(km + PICKUP_LEG_KM);
        return trip + SAFETY_MARGIN_MINUTES <= batch.minutesRemaining(ctx.now());
    }

    @Override
    protected double score(FoodBatch batch, RecipientSlot slot, MatchContext ctx) {
        double km = ctx.distanceKm(batch.getDonorId(), slot.recipient().getLocation());
        double share = Math.min(2.0, slot.recentServings() / (double) slot.recipient().getDailyCapacity());
        double fit = slot.remaining() >= batch.getRemainingServings() ? 1 : 0;
        return W_NEED * slot.recipient().getNeedLevel().getWeight() - W_DISTANCE * km - W_FAIRNESS * share
                + W_FIT * fit;
    }
}
