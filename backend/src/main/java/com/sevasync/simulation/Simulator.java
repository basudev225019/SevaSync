package com.sevasync.simulation;

import com.sevasync.matching.Allocation;
import com.sevasync.matching.MatchContext;
import com.sevasync.matching.MatchingStrategy;
import com.sevasync.matching.RecipientSlot;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.VehicleType;
import com.sevasync.validation.SafetyValidator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Discrete-time simulation of one campus day for one strategy. Every {@value #TICK_MINUTES} minutes:
 * <ol>
 *   <li>newly logged food becomes available;</li>
 *   <li>the strategy matches available food to recipients with room left today;</li>
 *   <li>free volunteers take matched jobs (most urgent first), ride to the donor and on to the recipient.</li>
 * </ol>
 * A job delivers only if the volunteer arrives before the food expires; everything else counts as wasted.
 */
final class Simulator {
    static final int TICK_MINUTES = 10;

    /** Raw numbers of one run. */
    record Outcome(int offered, int delivered, int wasted, double distanceKm, long deliveryMinutes, int deliveries,
                   int deliveredToUrgent, double fairness, int wastedUnmatched, int wastedWaiting, int wastedLate) {
    }

    private record Job(FoodBatch batch, Recipient recipient, int servings, double km, LocalDateTime matchedAt) {
    }

    private static final class Courier {
        final VehicleType vehicle;
        GeoPoint at;
        LocalDateTime freeAt;

        Courier(VehicleType vehicle, GeoPoint at, LocalDateTime freeAt) {
            this.vehicle = vehicle;
            this.at = at;
            this.freeAt = freeAt;
        }
    }

    private Simulator() {
    }

    static Outcome run(Scenario s, MatchingStrategy strategy) {
        Map<String, FoodBatch> batches = s.freshBatches();
        List<FoodBatch> available = new ArrayList<>();
        Map<String, Integer> used = new HashMap<>();
        List<Courier> couriers = new ArrayList<>();
        for (VehicleType v : s.volunteers) {
            couriers.add(new Courier(v, s.depot, s.start));
        }
        LinkedList<Job> queue = new LinkedList<>();
        int arrivalIdx = 0;
        int offered = 0;
        int delivered = 0;
        int wastedUnmatched = 0; // expired before any recipient had room for it
        int wastedWaiting = 0;   // matched, but no volunteer was free in time
        int wastedLate = 0;      // no free volunteer could reach the recipient before expiry
        int deliveries = 0;
        int toUrgent = 0;
        double km = 0;
        long minutes = 0;
        Map<String, Integer> deliveredTo = new HashMap<>();

        for (LocalDateTime t = s.start; t.isBefore(s.end); t = t.plusMinutes(TICK_MINUTES)) {
            // 1. new food arrives
            while (arrivalIdx < s.arrivals.size() && !s.arrivals.get(arrivalIdx).at().isAfter(t)) {
                FoodBatch b = batches.get(s.arrivals.get(arrivalIdx).id());
                available.add(b);
                offered += b.getServings();
                arrivalIdx++;
            }
            // 2. food that expired while waiting is lost
            final LocalDateTime now = t;
            for (FoodBatch b : new ArrayList<>(available)) {
                if (b.isExpiredAt(now)) {
                    wastedUnmatched += b.getRemainingServings();
                    available.remove(b);
                }
            }
            for (Job j : new ArrayList<>(queue)) {
                if (j.batch().isExpiredAt(now)) {
                    wastedWaiting += j.servings();
                    queue.remove(j);
                }
            }
            // 3. match
            List<RecipientSlot> slots = new ArrayList<>();
            for (Recipient r : s.recipients) {
                int free = r.getDailyCapacity() - used.getOrDefault(r.getId(), 0);
                if (free > 0) {
                    slots.add(new RecipientSlot(r, free, used.getOrDefault(r.getId(), 0)));
                }
            }
            MatchContext ctx = new MatchContext(now, s.donorLocations, SafetyValidator.MIN_DISPATCH_MINUTES,
                    VehicleType.BICYCLE);
            Map<String, Recipient> byId = new HashMap<>();
            s.recipients.forEach(r -> byId.put(r.getId(), r));
            for (Allocation a : strategy.allocate(available, slots, ctx)) {
                FoodBatch b = batches.get(a.batchId());
                b.allocate(a.servings());
                used.merge(a.recipientId(), a.servings(), Integer::sum);
                queue.add(new Job(b, byId.get(a.recipientId()), a.servings(), a.distanceKm(), now));
            }
            available.removeIf(b -> b.getRemainingServings() == 0);

            // 4. free volunteers take the most urgent jobs they can still finish in time
            queue.sort(Comparator.comparing(j -> j.batch().getExpiresAt()));
            for (Courier c : couriers) {
                if (c.freeAt.isAfter(now) || queue.isEmpty()) {
                    continue;
                }
                Job job = null;
                int travel = 0;
                for (Job candidate : queue) {
                    int minutesNeeded = travelMinutes(c, candidate, s);
                    if (!now.plusMinutes(minutesNeeded).isAfter(candidate.batch().getExpiresAt())) {
                        job = candidate;
                        travel = minutesNeeded;
                        break;
                    }
                }
                if (job == null) {
                    continue; // nothing this volunteer could deliver in time; keep them free for later work
                }
                queue.remove(job);
                LocalDateTime arrival = now.plusMinutes(travel);
                c.freeAt = arrival;
                c.at = job.recipient().getLocation();
                delivered += job.servings();
                deliveries++;
                km += job.km();
                minutes += Duration.between(job.matchedAt(), arrival).toMinutes();
                deliveredTo.merge(job.recipient().getId(), job.servings(), Integer::sum);
                if (job.recipient().getNeedLevel() != NeedLevel.NORMAL) {
                    toUrgent += job.servings();
                }
            }
        }
        // anything still unmatched or queued at the end of the day is wasted
        for (FoodBatch b : available) {
            wastedUnmatched += b.getRemainingServings();
        }
        for (Job j : queue) {
            // matched food nobody could carry: late if no volunteer could ever have made it, else waiting
            boolean reachable = false;
            for (Courier c : couriers) {
                reachable |= j.matchedAt().plusMinutes(travelMinutes(c, j, s)).isBefore(j.batch().getExpiresAt());
            }
            if (reachable) {
                wastedWaiting += j.servings();
            } else {
                wastedLate += j.servings();
            }
        }
        int wasted = wastedUnmatched + wastedWaiting + wastedLate;
        return new Outcome(offered, delivered, wasted, km, minutes, deliveries, toUrgent,
                jain(s.recipients, deliveredTo), wastedUnmatched, wastedWaiting, wastedLate);
    }

    /** Minutes for courier {@code c} to collect the job and deliver it (several trips if it does not fit). */
    private static int travelMinutes(Courier c, Job job, Scenario s) {
        GeoPoint donor = s.donorLocations.get(job.batch().getDonorId());
        int trips = (int) Math.ceil(job.servings() / (double) c.vehicle.getMaxServings());
        double legs = c.at.distanceKm(donor) + job.km() * (2 * trips - 1);
        return c.vehicle.tripMinutes(legs) + (trips - 1) * VehicleType.HANDLING_MINUTES;
    }

    /** Jain's fairness index over delivered/capacity ratios: 1.0 = perfectly even, 1/n = one recipient got it all. */
    private static double jain(List<Recipient> recipients, Map<String, Integer> delivered) {
        double sum = 0;
        double sumSq = 0;
        for (Recipient r : recipients) {
            double x = delivered.getOrDefault(r.getId(), 0) / (double) r.getDailyCapacity();
            sum += x;
            sumSq += x * x;
        }
        return sumSq == 0 ? 0 : (sum * sum) / (recipients.size() * sumSq);
    }
}
