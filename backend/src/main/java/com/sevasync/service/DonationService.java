package com.sevasync.service;

import com.sevasync.database.DatabaseManager;
import com.sevasync.event.Audience;
import com.sevasync.event.EventBus;
import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.IllegalTransitionException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.Permission;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.security.Guard;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Donors logging and withdrawing surplus food (workflow step 1: intake and safety validation). */
public class DonationService {

    /** Form data for a new batch. {@code preparedAt} null means "just now". */
    public record NewBatch(String foodName, FoodCategory category, boolean vegetarian, int servings,
                           LocalDateTime preparedAt, int shelfLifeMinutes, String notes) {
    }

    private final DatabaseManager db;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final SafetyValidator validator;
    private final AuditLogger audit;
    private final EventBus events;
    private final Clock clock;
    private final Runnable rematch;

    public DonationService(DatabaseManager db, SqliteFoodBatchRepository batches, SqliteDispatchRepository dispatches,
                           SafetyValidator validator, AuditLogger audit, EventBus events, Clock clock,
                           Runnable rematch) {
        this.db = db;
        this.batches = batches;
        this.dispatches = dispatches;
        this.validator = validator;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.rematch = rematch;
    }

    public FoodBatch logBatch(User donor, NewBatch form) throws SevaSyncException {
        Guard.require(donor, Permission.LOG_BATCH);
        String name = InputSanitizer.text(form.foodName(), "Food name", 80, true);
        String notes = InputSanitizer.text(form.notes(), "Notes", 240, false);
        LocalDateTime now = TimeUtil.now(clock);
        LocalDateTime prepared = form.preparedAt() == null ? now : form.preparedAt();
        validator.validateNewBatch(form.category(), form.servings(), prepared, form.shelfLifeMinutes());

        FoodBatch batch = db.inTransaction(() -> {
            FoodBatch b = new FoodBatch(batches.nextId(), donor.getId(), name, form.category(), form.vegetarian(),
                    form.servings(), form.servings(), prepared, form.shelfLifeMinutes(), BatchStatus.AVAILABLE,
                    notes, now);
            batches.save(b);
            audit.log(donor.getId(), "BATCH_LOGGED", "FoodBatch", b.getId(), b.getServings() + " x " + name
                    + " (" + form.category() + "), safe until " + b.getExpiresAt());
            events.publish("BATCH_LOGGED", "FoodBatch", b.getId(),
                    donor.getName() + " logged " + b.getServings() + " servings of " + name,
                    Map.of("donorId", donor.getId(), "servings", b.getServings()), Audience.everyone());
            return b;
        });
        rematch.run();
        return batches.findById(batch.getId()).orElse(batch);
    }

    /**
     * The donor takes a batch back (e.g. the food was eaten after all). Allowed while no volunteer has claimed any
     * part of it; open matches are cancelled.
     */
    public FoodBatch withdraw(User donor, String batchId, String reason) throws SevaSyncException {
        Guard.require(donor, Permission.WITHDRAW_BATCH);
        String why = InputSanitizer.text(reason, "Reason", 120, false);
        return db.inTransaction(() -> {
            FoodBatch b = get(batchId);
            if (!b.getDonorId().equals(donor.getId())) {
                throw new AccessDeniedException("You can only withdraw your own batches");
            }
            if (!b.getStatus().isOpen()) {
                throw new IllegalTransitionException("Batch " + batchId + " is " + b.getStatus()
                        + " and can no longer be withdrawn");
            }
            List<Dispatch> active = dispatches.findActiveByBatch(batchId);
            for (Dispatch d : active) {
                if (d.getStatus() != DispatchStatus.ASSIGNED) {
                    throw new IllegalTransitionException("A volunteer is already on the way for this batch");
                }
            }
            for (Dispatch d : active) {
                d.cancel("Withdrawn by donor" + (why.isEmpty() ? "" : ": " + why));
                dispatches.save(d);
                events.publish("DISPATCH_CANCELLED", "Dispatch", d.getId(), "Match cancelled: food withdrawn",
                        Map.of("batchId", batchId), Audience.users(d.getRecipientId()).and(Audience.roles(Role.VOLUNTEER)));
            }
            b.markWithdrawn();
            batches.save(b);
            audit.log(donor.getId(), "BATCH_WITHDRAWN", "FoodBatch", batchId, why);
            events.publish("BATCH_WITHDRAWN", "FoodBatch", batchId, b.getFoodName() + " was withdrawn",
                    Map.of(), Audience.everyone());
            return b;
        });
    }

    /** Donors see their own batches, administrators see all (optionally by status). */
    public List<FoodBatch> list(User viewer, BatchStatus status) throws SevaSyncException {
        if (viewer.can(Permission.VIEW_ALL_BATCHES)) {
            return status == null ? batches.findAll() : batches.findByStatus(status);
        }
        Guard.require(viewer, Permission.VIEW_OWN_BATCHES);
        List<FoodBatch> own = batches.findByDonor(viewer.getId());
        return status == null ? own : own.stream().filter(b -> b.getStatus() == status).toList();
    }

    public FoodBatch get(User viewer, String id) throws SevaSyncException {
        FoodBatch b = get(id);
        Guard.requireSelfOr(viewer, b.getDonorId(), Permission.VIEW_ALL_BATCHES);
        return b;
    }

    FoodBatch get(String id) throws EntityNotFoundException {
        return batches.findById(id).orElseThrow(() -> new EntityNotFoundException("Food batch", id));
    }
}
