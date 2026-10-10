package com.sevasync.service;

import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.Repository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Donor registration and surplus-batch intake (step 01 and 02 of the SevaSync flow). */
public class DonationService {
    private final Repository<Donor> donors;
    private final Repository<FoodBatch> batches;
    private final SafetyValidator validator;
    private final AuditLogger audit;
    private final Clock clock;
    private final ReentrantLock lock;

    public DonationService(Repository<Donor> donors, Repository<FoodBatch> batches, SafetyValidator validator,
                           AuditLogger audit, Clock clock, ReentrantLock lock) {
        this.donors = donors;
        this.batches = batches;
        this.validator = validator;
        this.audit = audit;
        this.clock = clock;
        this.lock = lock;
    }

    public Donor registerDonor(String name, String phone, String email, String address, DonorType type)
            throws InvalidInputException {
        String cleanName = InputSanitizer.text(name, "Name", 80, true);
        String cleanPhone = InputSanitizer.phone(phone, "Phone");
        String cleanEmail = InputSanitizer.email(email, "E-mail");
        String cleanAddress = InputSanitizer.text(address, "Address", 160, false);
        if (type == null) {
            throw new InvalidInputException("Donor type is required");
        }
        lock.lock();
        try {
            Donor donor = new Donor(donors.nextId(), cleanName, cleanPhone, cleanEmail, cleanAddress, type,
                    TimeUtil.now(clock));
            donors.save(donor);
            audit.log("DONOR_REGISTERED", "Donor", donor.getId(), cleanName + " (" + type + ")");
            return donor;
        } finally {
            lock.unlock();
        }
    }

    public List<Donor> listDonors() {
        return donors.findAll();
    }

    public Donor getDonor(String id) throws EntityNotFoundException {
        return donors.findById(id).orElseThrow(() -> new EntityNotFoundException("Donor", id));
    }

    /**
     * Logs a surplus batch. The batch is checked by the {@link SafetyValidator}; compromised batches are rejected
     * (and the rejection audited) before they can enter the distribution cycle.
     */
    public FoodBatch submitBatch(String donorId, String foodName, FoodCategory category, boolean vegetarian,
                                 int servings, LocalDateTime preparedAt, int shelfLifeMinutes) throws SevaSyncException {
        String cleanFood = InputSanitizer.text(foodName, "Food name", 80, true);
        lock.lock();
        try {
            Donor donor = getDonor(donorId);
            try {
                validator.validateNewBatch(category, servings, preparedAt, shelfLifeMinutes);
            } catch (SevaSyncException e) {
                audit.log("BATCH_REJECTED", "FoodBatch", "-", donor.getId() + " / " + cleanFood + ": " + e.getMessage());
                throw e;
            }
            FoodBatch batch = new FoodBatch(batches.nextId(), donor.getId(), cleanFood, category, vegetarian,
                    servings, servings, preparedAt, shelfLifeMinutes, BatchStatus.AVAILABLE, TimeUtil.now(clock));
            batches.save(batch);
            audit.log("BATCH_CREATED", "FoodBatch", batch.getId(), servings + " servings of " + cleanFood
                    + " from " + donor.getId() + ", expires " + batch.getExpiresAt());
            return batch;
        } finally {
            lock.unlock();
        }
    }

    public List<FoodBatch> listBatches(BatchStatus statusOrNull) {
        return statusOrNull == null ? batches.findAll() : batches.findWhere(b -> b.getStatus() == statusOrNull);
    }

    public FoodBatch getBatch(String id) throws EntityNotFoundException {
        return batches.findById(id).orElseThrow(() -> new EntityNotFoundException("Food batch", id));
    }
}
