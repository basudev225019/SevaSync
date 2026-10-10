package com.sevasync.service;

import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Recipient;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.Repository;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority-driven matching engine (step 03).
 *
 * <ul>
 *   <li>Batches sit in a {@link PriorityQueue} ordered by <b>earliest expiry first</b> (shortest remaining shelf-life).</li>
 *   <li>Recipients sit in a second {@link PriorityQueue} ordered by <b>need level</b> (CRITICAL first), then by the
 *       free capacity they still have today.</li>
 *   <li>The most urgent batch is handed to the most needy recipient; a batch is split over several recipients when
 *       one cannot take all of it, and a recipient with spare capacity goes back into the queue.</li>
 * </ul>
 * Complexity: O((B + R + A) log (B + R)) for B batches, R recipients and A allocations.
 */
public class MatchingService {
    /** Batches closer to expiry than this are not worth dispatching any more. */
    public static final int MIN_DISPATCH_MINUTES = 10;

    private static final Comparator<FoodBatch> BATCH_ORDER = Comparator
            .comparing(FoodBatch::getExpiresAt)
            .thenComparing(Comparator.comparingInt(FoodBatch::getRemainingServings).reversed())
            .thenComparing(FoodBatch::getId);

    private static final Comparator<Slot> SLOT_ORDER = (a, b) -> {
        int byNeed = Integer.compare(b.recipient.getNeedLevel().getWeight(), a.recipient.getNeedLevel().getWeight());
        if (byNeed != 0) {
            return byNeed;
        }
        int byFree = Integer.compare(b.remaining, a.remaining);
        return byFree != 0 ? byFree : a.recipient.getId().compareTo(b.recipient.getId());
    };

    /** A recipient together with the servings it can still absorb today. */
    private static final class Slot {
        private final Recipient recipient;
        private int remaining;

        private Slot(Recipient recipient, int remaining) {
            this.recipient = recipient;
            this.remaining = remaining;
        }
    }

    private record Allocation(String batchId, String recipientId, int servings) {
    }

    private record Plan(List<Allocation> allocations, Map<String, FoodBatch> touchedBatches, int unallocated,
                        int batchesConsidered, int recipientsAvailable) {
    }

    public record MatchResult(boolean committed, List<Dispatch> dispatches, int unallocatedServings,
                              int batchesConsidered, int recipientsAvailable) {
        public int allocatedServings() {
            return dispatches.stream().mapToInt(Dispatch::getServings).sum();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("committed", committed);
            m.put("batchesConsidered", batchesConsidered);
            m.put("recipientsAvailable", recipientsAvailable);
            m.put("allocatedServings", allocatedServings());
            m.put("unallocatedServings", unallocatedServings);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Dispatch d : dispatches) {
                list.add(d.toMap());
            }
            m.put("dispatches", list);
            return m;
        }
    }

    private final Repository<FoodBatch> batches;
    private final Repository<Recipient> recipients;
    private final Repository<Dispatch> dispatches;
    private final RecipientService recipientService;
    private final DispatchService dispatchService;
    private final AuditLogger audit;
    private final Clock clock;
    private final ReentrantLock lock;

    public MatchingService(Repository<FoodBatch> batches, Repository<Recipient> recipients,
                           Repository<Dispatch> dispatches, RecipientService recipientService,
                           DispatchService dispatchService, AuditLogger audit, Clock clock, ReentrantLock lock) {
        this.batches = batches;
        this.recipients = recipients;
        this.dispatches = dispatches;
        this.recipientService = recipientService;
        this.dispatchService = dispatchService;
        this.audit = audit;
        this.clock = clock;
        this.lock = lock;
    }

    /** Dry run: shows what {@link #runMatching()} would do without saving anything. */
    public MatchResult preview() {
        lock.lock();
        try {
            LocalDateTime now = TimeUtil.now(clock);
            Plan plan = plan(now);
            List<Dispatch> out = new ArrayList<>();
            int i = 1;
            for (Allocation a : plan.allocations()) {
                out.add(Dispatch.assigned("PREVIEW-" + i++, a.batchId(), a.recipientId(), a.servings(), now));
            }
            return new MatchResult(false, out, plan.unallocated(), plan.batchesConsidered(), plan.recipientsAvailable());
        } finally {
            lock.unlock();
        }
    }

    /** Runs the matching engine and persists the resulting dispatches. */
    public MatchResult runMatching() {
        lock.lock();
        try {
            dispatchService.expireOverdue();
            LocalDateTime now = TimeUtil.now(clock);
            Plan plan = plan(now);
            List<String> ids = dispatches.nextIds(plan.allocations().size());
            List<Dispatch> created = new ArrayList<>();
            for (int i = 0; i < plan.allocations().size(); i++) {
                Allocation a = plan.allocations().get(i);
                created.add(Dispatch.assigned(ids.get(i), a.batchId(), a.recipientId(), a.servings(), now));
            }
            if (!created.isEmpty()) {
                dispatches.saveAll(created);
                batches.saveAll(plan.touchedBatches().values());
                for (Dispatch d : created) {
                    audit.log("DISPATCH_ASSIGNED", "Dispatch", d.getId(),
                            d.getServings() + " servings of " + d.getBatchId() + " -> " + d.getRecipientId() + " (auto)");
                }
            }
            MatchResult result = new MatchResult(true, created, plan.unallocated(), plan.batchesConsidered(),
                    plan.recipientsAvailable());
            audit.log("MATCHING_RUN", "System", "-", created.size() + " dispatches, " + result.allocatedServings()
                    + " servings allocated, " + plan.unallocated() + " unallocated");
            return result;
        } finally {
            lock.unlock();
        }
    }

    private Plan plan(LocalDateTime now) {
        PriorityQueue<FoodBatch> batchQueue = new PriorityQueue<>(BATCH_ORDER);
        for (FoodBatch b : batches.findAll()) {
            if (b.getStatus() == BatchStatus.AVAILABLE && b.getRemainingServings() > 0
                    && b.minutesRemaining(now) >= MIN_DISPATCH_MINUTES) {
                batchQueue.add(b);
            }
        }
        int considered = batchQueue.size();

        Map<String, Integer> used = recipientService.usedTodayByRecipient();
        PriorityQueue<Slot> slotQueue = new PriorityQueue<>(SLOT_ORDER);
        for (Recipient r : recipients.findAll()) {
            int free = r.getDailyCapacity() - used.getOrDefault(r.getId(), 0);
            if (free > 0) {
                slotQueue.add(new Slot(r, free));
            }
        }
        int recipientsAvailable = slotQueue.size();

        List<Allocation> allocations = new ArrayList<>();
        Map<String, FoodBatch> touched = new LinkedHashMap<>();
        int unallocated = 0;
        while (!batchQueue.isEmpty()) {
            FoodBatch batch = batchQueue.poll();
            LinkedList<Slot> withSpareCapacity = new LinkedList<>();
            while (batch.getRemainingServings() > 0 && !slotQueue.isEmpty()) {
                Slot slot = slotQueue.poll();
                int give = Math.min(batch.getRemainingServings(), slot.remaining);
                allocations.add(new Allocation(batch.getId(), slot.recipient.getId(), give));
                batch.allocate(give);
                touched.put(batch.getId(), batch);
                slot.remaining -= give;
                if (slot.remaining > 0) {
                    withSpareCapacity.add(slot);
                }
            }
            slotQueue.addAll(withSpareCapacity);
            unallocated += batch.getRemainingServings();
        }
        return new Plan(allocations, touched, unallocated, considered, recipientsAvailable);
    }
}
