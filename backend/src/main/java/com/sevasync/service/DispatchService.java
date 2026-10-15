package com.sevasync.service;

import com.sevasync.database.DatabaseManager;
import com.sevasync.event.Audience;
import com.sevasync.event.EventBus;
import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.CapacityExceededException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.IllegalTransitionException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Permission;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.security.Guard;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteDonorRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteRecipientRepository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dispatch life-cycle (workflow steps 3-5): volunteers claim, pick up and deliver; recipients confirm;
 * administrators assign manually or cancel; the expiry monitor cancels whatever can no longer be delivered safely.
 *
 * <p>Every public operation runs in one transaction, and the state rules themselves live in
 * {@link com.sevasync.model.DispatchState}: this class only adds the checks that need other data
 * (who is asking, vehicle size, food freshness).
 */
public class DispatchService {
    private final DatabaseManager db;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final SqliteRecipientRepository recipients;
    private final SqliteDonorRepository donors;
    private final RecipientService capacity;
    private final SafetyValidator validator;
    private final AuditLogger audit;
    private final EventBus events;
    private final Clock clock;
    private final Runnable rematch;

    public DispatchService(DatabaseManager db, SqliteFoodBatchRepository batches, SqliteDispatchRepository dispatches,
                           SqliteRecipientRepository recipients, SqliteDonorRepository donors,
                           RecipientService capacity, SafetyValidator validator, AuditLogger audit, EventBus events,
                           Clock clock, Runnable rematch) {
        this.db = db;
        this.batches = batches;
        this.dispatches = dispatches;
        this.recipients = recipients;
        this.donors = donors;
        this.capacity = capacity;
        this.validator = validator;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.rematch = rematch;
    }

    // ------------------------------------------------------------------ queries

    public Dispatch get(String id) throws EntityNotFoundException {
        return dispatches.findById(id).orElseThrow(() -> new EntityNotFoundException("Dispatch", id));
    }

    /** A dispatch, if the viewer is involved in it (or an administrator). */
    public Dispatch get(User viewer, String id) throws SevaSyncException {
        Dispatch d = get(id);
        if (viewer.can(Permission.VIEW_ALL_DISPATCHES) || viewer.getId().equals(d.getRecipientId())
                || viewer.getId().equals(d.getVolunteerId())
                || (viewer.getRole() == Role.VOLUNTEER && d.getStatus() == DispatchStatus.ASSIGNED)
                || (viewer instanceof Donor && batches.findById(d.getBatchId())
                .map(b -> b.getDonorId().equals(viewer.getId())).orElse(false))) {
            return d;
        }
        throw new AccessDeniedException("You are not involved in this dispatch");
    }

    /** What each role sees in "my dispatches". */
    public List<Dispatch> listFor(User viewer, DispatchStatus status) throws SevaSyncException {
        List<Dispatch> list;
        if (viewer.can(Permission.VIEW_ALL_DISPATCHES)) {
            list = status == null ? dispatches.findAll() : dispatches.findByStatus(status);
            return list;
        }
        switch (viewer.getRole()) {
            case VOLUNTEER -> list = dispatches.findByVolunteer(viewer.getId());
            case RECIPIENT -> {
                Guard.require(viewer, Permission.VIEW_INCOMING);
                list = dispatches.findByRecipient(viewer.getId());
            }
            case DONOR -> {
                Guard.require(viewer, Permission.VIEW_OWN_BATCHES);
                list = dispatches.findByDonor(viewer.getId());
            }
            default -> throw new AccessDeniedException("No dispatch view for this role");
        }
        return status == null ? list : list.stream().filter(d -> d.getStatus() == status).toList();
    }

    /** Unclaimed pickups, nearest first, for a volunteer's board. */
    public List<Dispatch> openPickups(Volunteer volunteer) throws SevaSyncException {
        Guard.require(volunteer, Permission.VIEW_OPEN_PICKUPS);
        LocalDateTime now = TimeUtil.now(clock);
        List<Dispatch> open = new ArrayList<>(dispatches.findByStatus(DispatchStatus.ASSIGNED));
        Map<String, FoodBatch> byId = batches.findByIds(open.stream().map(Dispatch::getBatchId).toList());
        open.removeIf(d -> byId.get(d.getBatchId()) == null || byId.get(d.getBatchId()).isExpiredAt(now));
        open.sort(Comparator.comparing(d -> byId.get(d.getBatchId()).getExpiresAt()));
        return open;
    }

    // ------------------------------------------------------------------ volunteer actions

    public Dispatch claim(Volunteer volunteer, String dispatchId) throws SevaSyncException {
        Guard.require(volunteer, Permission.CLAIM_DISPATCH);
        return db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            if (d.getStatus() != DispatchStatus.ASSIGNED) {
                throw new IllegalTransitionException("Someone else already took this pickup");
            }
            if (dispatches.countActiveByVolunteer(volunteer.getId()) >= Volunteer.MAX_ACTIVE_DISPATCHES) {
                throw new CapacityExceededException("Finish one of your " + Volunteer.MAX_ACTIVE_DISPATCHES
                        + " active pickups before claiming another");
            }
            if (!volunteer.canCarry(d.getServings())) {
                throw new CapacityExceededException("A " + volunteer.getVehicle() + " carries at most "
                        + volunteer.getVehicle().getMaxServings() + " servings; this pickup has " + d.getServings());
            }
            FoodBatch b = batch(d);
            validator.requireFresh(b);
            d.claim(volunteer.getId(), TimeUtil.now(clock));
            dispatches.save(d);
            audit.log(volunteer.getId(), "DISPATCH_CLAIMED", "Dispatch", d.getId(), volunteer.getName()
                    + " will carry " + d.getServings() + " servings of " + b.getFoodName());
            publish(d, b, "DISPATCH_CLAIMED", volunteer.getName() + " is heading to pick up " + b.getFoodName(),
                    Audience.roles(Role.VOLUNTEER));
            return d;
        });
    }

    public Dispatch release(Volunteer volunteer, String dispatchId) throws SevaSyncException {
        Guard.require(volunteer, Permission.PROGRESS_DISPATCH);
        return db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            d.release(volunteer.getId());
            dispatches.save(d);
            FoodBatch b = batch(d);
            audit.log(volunteer.getId(), "DISPATCH_RELEASED", "Dispatch", d.getId(), "back on the board");
            publish(d, b, "DISPATCH_RELEASED", "A pickup of " + b.getFoodName() + " is open again",
                    Audience.roles(Role.VOLUNTEER));
            return d;
        });
    }

    public Dispatch pickUp(Volunteer volunteer, String dispatchId) throws SevaSyncException {
        Guard.require(volunteer, Permission.PROGRESS_DISPATCH);
        refuseIfExpired(dispatchId);
        return db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            FoodBatch b = batch(d);
            d.pickUp(volunteer.getId(), TimeUtil.now(clock));
            dispatches.save(d);
            audit.log(volunteer.getId(), "DISPATCH_PICKED_UP", "Dispatch", d.getId(), d.getServings()
                    + " servings collected from donor");
            publish(d, b, "DISPATCH_PICKED_UP", b.getFoodName() + " is on its way", Audience.admins());
            return d;
        });
    }

    public Dispatch deliver(Volunteer volunteer, String dispatchId) throws SevaSyncException {
        Guard.require(volunteer, Permission.PROGRESS_DISPATCH);
        refuseIfExpired(dispatchId);
        return db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            FoodBatch b = batch(d);
            d.deliver(volunteer.getId(), TimeUtil.now(clock));
            dispatches.save(d);
            if (b.getStatus() == BatchStatus.ALLOCATED && dispatches.findActiveByBatch(b.getId()).isEmpty()) {
                b.markCompleted();
                batches.save(b);
            }
            audit.log(volunteer.getId(), "DISPATCH_DELIVERED", "Dispatch", d.getId(), d.getServings()
                    + " servings of " + b.getFoodName() + " handed over by " + volunteer.getName());
            publish(d, b, "DISPATCH_DELIVERED", d.getServings() + " servings of " + b.getFoodName() + " delivered",
                    Audience.everyone());
            return d;
        });
    }

    // ------------------------------------------------------------------ recipient and admin actions

    public Dispatch confirmReceipt(User recipient, String dispatchId, int rating, String feedback)
            throws SevaSyncException {
        Guard.require(recipient, Permission.CONFIRM_RECEIPT);
        String text = InputSanitizer.text(feedback, "Feedback", 280, false);
        return db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            if (!d.getRecipientId().equals(recipient.getId())) {
                throw new AccessDeniedException("This delivery was not addressed to you");
            }
            d.confirm(rating, text, TimeUtil.now(clock));
            dispatches.save(d);
            FoodBatch b = batch(d);
            audit.log(recipient.getId(), "DISPATCH_CONFIRMED", "Dispatch", d.getId(), rating + "/5"
                    + (text.isEmpty() ? "" : " - " + text));
            publish(d, b, "DISPATCH_CONFIRMED", recipient.getName() + " rated " + b.getFoodName() + " " + rating + "/5",
                    Audience.admins());
            return d;
        });
    }

    public Dispatch cancel(User actor, String dispatchId, String reason) throws SevaSyncException {
        Guard.require(actor, Permission.CANCEL_ANY_DISPATCH);
        String why = InputSanitizer.text(reason, "Reason", 120, false);
        Dispatch result = db.inTransaction(() -> {
            Dispatch d = get(dispatchId);
            DispatchStatus before = d.getStatus();
            d.cancel(why.isEmpty() ? "Cancelled by coordinator" : why);
            dispatches.save(d);
            FoodBatch b = batch(d);
            if (before != DispatchStatus.PICKED_UP) {
                // the food never left the donor, so it goes back into the pool
                b.release(d.getServings());
                batches.save(b);
            }
            audit.log(actor.getId(), "DISPATCH_CANCELLED", "Dispatch", d.getId(), d.getNote());
            publish(d, b, "DISPATCH_CANCELLED", "Dispatch of " + b.getFoodName() + " was cancelled",
                    Audience.roles(Role.VOLUNTEER));
            return d;
        });
        rematch.run();
        return result;
    }

    /** Administrator override of the matching engine. Safety and capacity rules still apply. */
    public Dispatch assignManually(User actor, String batchId, String recipientId, int servings)
            throws SevaSyncException {
        Guard.require(actor, Permission.ASSIGN_MANUALLY);
        InputSanitizer.range(servings, "Servings", 1, SafetyValidator.MAX_SERVINGS);
        return db.inTransaction(() -> {
            FoodBatch b = batches.findById(batchId).orElseThrow(() -> new EntityNotFoundException("Food batch", batchId));
            Recipient r = recipients.findById(recipientId)
                    .orElseThrow(() -> new EntityNotFoundException("Recipient", recipientId));
            validator.requireFresh(b);
            if (b.getStatus() != BatchStatus.AVAILABLE || b.getRemainingServings() < servings) {
                throw new CapacityExceededException("Batch " + batchId + " has only "
                        + (b.getStatus() == BatchStatus.AVAILABLE ? b.getRemainingServings() : 0) + " servings left");
            }
            int free = capacity.remainingToday(r);
            if (servings > free) {
                throw new CapacityExceededException(r.getName() + " can accept only " + free
                        + " more servings today (daily capacity " + r.getDailyCapacity() + ")");
            }
            double km = donors.findById(b.getDonorId()).map(dn -> dn.distanceKmTo(r)).orElse(0.0);
            Dispatch d = Dispatch.assigned(dispatches.nextId(), batchId, recipientId, servings, "manual", km,
                    TimeUtil.now(clock));
            b.allocate(servings);
            dispatches.save(d);
            batches.save(b);
            audit.log(actor.getId(), "DISPATCH_ASSIGNED", "Dispatch", d.getId(),
                    servings + " servings of " + batchId + " -> " + recipientId + " (manual)");
            publish(d, b, "DISPATCH_ASSIGNED", "New pickup: " + servings + " servings of " + b.getFoodName(),
                    Audience.roles(Role.VOLUNTEER));
            return d;
        });
    }

    // ------------------------------------------------------------------ expiry

    /**
     * Marks every batch past its consumption window as EXPIRED and cancels its undelivered dispatches.
     * Called by the background {@link ExpiryMonitor} and before every matching run.
     *
     * @return number of batches that expired
     */
    public int expireOverdue() {
        return db.inTransaction(() -> {
            LocalDateTime now = TimeUtil.now(clock);
            List<FoodBatch> overdue = batches.findOverdue(now);
            for (FoodBatch b : overdue) {
                int delivered = 0;
                for (Dispatch d : dispatches.findByBatch(b.getId())) {
                    if (d.getStatus() == DispatchStatus.DELIVERED) {
                        delivered += d.getServings();
                    } else if (d.isActive()) {
                        cancelQuietly(d, "Food expired before delivery");
                        dispatches.save(d);
                        audit.log(AuditLogger.SYSTEM, "DISPATCH_CANCELLED", "Dispatch", d.getId(), d.getNote());
                        events.publish("DISPATCH_CANCELLED", "Dispatch", d.getId(), "A pickup expired before delivery",
                                Map.of("batchId", b.getId()), audienceFor(d, b));
                    }
                }
                int wasted = b.getServings() - delivered;
                b.markExpired(wasted);
                batches.save(b);
                audit.log(AuditLogger.SYSTEM, "BATCH_EXPIRED", "FoodBatch", b.getId(), wasted + " servings wasted");
                events.publish("BATCH_EXPIRED", "FoodBatch", b.getId(), b.getFoodName() + " expired ("
                        + wasted + " servings lost)", Map.of("wasted", wasted), Audience.users(b.getDonorId())
                        .and(Audience.admins()));
            }
            return overdue.size();
        });
    }

    // ------------------------------------------------------------------ helpers

    private void refuseIfExpired(String dispatchId) throws SevaSyncException {
        Dispatch d = get(dispatchId);
        FoodBatch b = batch(d);
        if (validator.isExpired(b)) {
            expireOverdue(); // commits the expiry and the cancellation before we report it
            throw new ExpiredBatchException(b.getFoodName() + " passed its safe window at "
                    + b.getExpiresAt().toLocalTime() + "; the dispatch was cancelled and must not be handed over");
        }
    }

    private static void cancelQuietly(Dispatch d, String reason) {
        try {
            d.cancel(reason);
        } catch (IllegalTransitionException e) {
            throw new IllegalStateException(e); // only called for active dispatches, which can always be cancelled
        }
    }

    private FoodBatch batch(Dispatch d) throws EntityNotFoundException {
        return batches.findById(d.getBatchId()).orElseThrow(() -> new EntityNotFoundException("Food batch",
                d.getBatchId()));
    }

    private static Audience audienceFor(Dispatch d, FoodBatch b) {
        return Audience.users(d.getRecipientId(), d.getVolunteerId(), b.getDonorId());
    }

    private void publish(Dispatch d, FoodBatch b, String type, String message, Audience extra) {
        events.publish(type, "Dispatch", d.getId(), message,
                Map.of("status", d.getStatus(), "batchId", b.getId(), "servings", d.getServings()),
                audienceFor(d, b).and(extra));
    }

    /** Validates a rating coming from JSON (a number from 1 to 5). */
    public static int parseRating(Object raw) throws InvalidInputException {
        if (raw instanceof Number n) {
            return InputSanitizer.range(n.intValue(), "Rating", 1, 5);
        }
        throw new InvalidInputException("rating must be a number from 1 to 5");
    }
}
