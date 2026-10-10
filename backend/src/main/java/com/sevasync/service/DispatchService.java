package com.sevasync.service;

import com.sevasync.exception.CapacityExceededException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Recipient;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.Repository;
import com.sevasync.storage.CsvRepository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Dispatch life-cycle: assign, deliver, cancel and expire (step 04 - dispatch and audit). */
public class DispatchService {
    private final Repository<FoodBatch> batches;
    private final Repository<Dispatch> dispatches;
    private final RecipientService recipientService;
    private final SafetyValidator validator;
    private final AuditLogger audit;
    private final Clock clock;
    private final ReentrantLock lock;

    public DispatchService(Repository<FoodBatch> batches, Repository<Dispatch> dispatches,
                           RecipientService recipientService, SafetyValidator validator, AuditLogger audit,
                           Clock clock, ReentrantLock lock) {
        this.batches = batches;
        this.dispatches = dispatches;
        this.recipientService = recipientService;
        this.validator = validator;
        this.audit = audit;
        this.clock = clock;
        this.lock = lock;
    }

    public List<Dispatch> list(DispatchStatus statusOrNull) {
        return statusOrNull == null ? dispatches.findAll() : dispatches.findWhere(d -> d.getStatus() == statusOrNull);
    }

    public Dispatch get(String id) throws EntityNotFoundException {
        return dispatches.findById(id).orElseThrow(() -> new EntityNotFoundException("Dispatch", id));
    }

    /** Manual allocation (overrides the matching engine, still respecting safety and capacity rules). */
    public Dispatch assignManually(String batchId, String recipientId, int servings) throws SevaSyncException {
        InputSanitizer.range(servings, "Servings", 1, SafetyValidator.MAX_SERVINGS);
        lock.lock();
        try {
            expireOverdue();
            FoodBatch batch = batches.findById(batchId)
                    .orElseThrow(() -> new EntityNotFoundException("Food batch", batchId));
            Recipient recipient = recipientService.get(recipientId);
            if (batch.getStatus() == BatchStatus.EXPIRED) {
                throw new ExpiredBatchException("Batch " + batchId + " has expired");
            }
            validator.validateForDispatch(batch);
            if (batch.getStatus() != BatchStatus.AVAILABLE || batch.getRemainingServings() < servings) {
                throw new CapacityExceededException("Batch " + batchId + " has only "
                        + (batch.getStatus() == BatchStatus.AVAILABLE ? batch.getRemainingServings() : 0)
                        + " servings left");
            }
            int free = recipientService.remainingCapacityToday(recipient);
            if (servings > free) {
                throw new CapacityExceededException(recipient.getName() + " can accept only " + free
                        + " more servings today (daily capacity " + recipient.getDailyCapacity() + ")");
            }
            Dispatch d = Dispatch.assigned(dispatches.nextId(), batchId, recipientId, servings, TimeUtil.now(clock));
            batch.allocate(servings);
            dispatches.save(d);
            batches.save(batch);
            audit.log("DISPATCH_ASSIGNED", "Dispatch", d.getId(),
                    servings + " servings of " + batchId + " -> " + recipientId + " (manual)");
            return d;
        } finally {
            lock.unlock();
        }
    }

    /** Volunteer confirms hand-over. Refused (and cancelled) if the batch expired in the meantime. */
    public Dispatch markDelivered(String dispatchId, String volunteerName) throws SevaSyncException {
        String volunteer = InputSanitizer.text(volunteerName, "Volunteer name", 60, true);
        lock.lock();
        try {
            Dispatch d = get(dispatchId);
            if (d.getStatus() != DispatchStatus.ASSIGNED) {
                throw new InvalidInputException("Dispatch " + dispatchId + " is already " + d.getStatus());
            }
            FoodBatch batch = batches.findById(d.getBatchId())
                    .orElseThrow(() -> new EntityNotFoundException("Food batch", d.getBatchId()));
            if (validator.isExpired(batch)) {
                expireOverdue(); // cancels this dispatch and writes the audit rows
                throw new ExpiredBatchException("Batch " + batch.getId() + " expired at " + batch.getExpiresAt()
                        + "; dispatch " + dispatchId + " was cancelled");
            }
            d.markDelivered(TimeUtil.now(clock), volunteer);
            dispatches.save(d);
            boolean stillPending = dispatches.findWhere(x -> x.getBatchId().equals(batch.getId())
                    && x.getStatus() == DispatchStatus.ASSIGNED).size() > 0;
            if (batch.getRemainingServings() == 0 && !stillPending) {
                batch.setStatus(BatchStatus.COMPLETED);
                batches.save(batch);
            }
            audit.log("DISPATCH_DELIVERED", "Dispatch", d.getId(),
                    d.getServings() + " servings of " + batch.getId() + " delivered to " + d.getRecipientId()
                            + " by " + volunteer);
            return d;
        } finally {
            lock.unlock();
        }
    }

    public Dispatch cancel(String dispatchId, String reason) throws SevaSyncException {
        String cleanReason = InputSanitizer.text(reason, "Reason", 120, false);
        lock.lock();
        try {
            Dispatch d = get(dispatchId);
            if (d.getStatus() != DispatchStatus.ASSIGNED) {
                throw new InvalidInputException("Only ASSIGNED dispatches can be cancelled (this one is "
                        + d.getStatus() + ")");
            }
            FoodBatch batch = batches.findById(d.getBatchId())
                    .orElseThrow(() -> new EntityNotFoundException("Food batch", d.getBatchId()));
            d.cancel(cleanReason.isEmpty() ? "Cancelled" : cleanReason);
            dispatches.save(d);
            if (batch.getStatus() != BatchStatus.EXPIRED) {
                batch.release(d.getServings());
                batches.save(batch);
            }
            audit.log("DISPATCH_CANCELLED", "Dispatch", d.getId(), d.getNote());
            return d;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks every batch that passed its consumption window as EXPIRED and cancels its undelivered dispatches.
     * Called by the background {@code ExpiryMonitor} and before every matching run.
     *
     * @return number of batches that expired
     */
    public int expireOverdue() {
        lock.lock();
        try {
            LocalDateTime now = TimeUtil.now(clock);
            List<FoodBatch> overdue = batches.findWhere(b ->
                    (b.getStatus() == BatchStatus.AVAILABLE || b.getStatus() == BatchStatus.ALLOCATED)
                            && b.isExpiredAt(now));
            if (overdue.isEmpty()) {
                return 0;
            }
            List<Dispatch> all = dispatches.findAll();
            List<Dispatch> changed = new ArrayList<>();
            for (FoodBatch b : overdue) {
                int delivered = 0;
                for (Dispatch d : all) {
                    if (!d.getBatchId().equals(b.getId())) {
                        continue;
                    }
                    if (d.getStatus() == DispatchStatus.DELIVERED) {
                        delivered += d.getServings();
                    } else if (d.getStatus() == DispatchStatus.ASSIGNED) {
                        d.cancel("Batch expired before delivery");
                        changed.add(d);
                        audit.log("DISPATCH_CANCELLED", "Dispatch", d.getId(), d.getNote());
                    }
                }
                int wasted = b.getServings() - delivered;
                b.setRemainingServings(wasted);
                b.setStatus(BatchStatus.EXPIRED);
                audit.log("BATCH_EXPIRED", "FoodBatch", b.getId(), wasted + " servings wasted");
            }
            dispatches.saveAll(changed);
            batches.saveAll(overdue);
            return overdue.size();
        } finally {
            lock.unlock();
        }
    }
}
