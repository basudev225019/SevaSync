package com.sevasync.model;

import com.sevasync.contract.Entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A batch of surplus food logged by a donor. The expiry time is derived, never entered directly. */
public class FoodBatch implements Entity {
    private final String id;
    private final String donorId;
    private final String foodName;
    private final FoodCategory category;
    private final boolean vegetarian;
    private final int servings;
    private int remainingServings;
    private final LocalDateTime preparedAt;
    private final int shelfLifeMinutes;
    private final LocalDateTime expiresAt;
    private BatchStatus status;
    private final String notes;
    private final LocalDateTime createdAt;

    public FoodBatch(String id, String donorId, String foodName, FoodCategory category, boolean vegetarian,
                     int servings, int remainingServings, LocalDateTime preparedAt, int shelfLifeMinutes,
                     BatchStatus status, String notes, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id);
        this.donorId = Objects.requireNonNull(donorId);
        this.foodName = Objects.requireNonNull(foodName);
        this.category = Objects.requireNonNull(category);
        this.vegetarian = vegetarian;
        if (servings <= 0 || remainingServings < 0 || remainingServings > servings || shelfLifeMinutes <= 0) {
            throw new IllegalArgumentException("Inconsistent batch quantities");
        }
        this.servings = servings;
        this.remainingServings = remainingServings;
        this.preparedAt = Objects.requireNonNull(preparedAt);
        this.shelfLifeMinutes = shelfLifeMinutes;
        this.expiresAt = preparedAt.plusMinutes(shelfLifeMinutes);
        this.status = Objects.requireNonNull(status);
        this.notes = notes == null ? "" : notes;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public String getId() { return id; }
    public String getDonorId() { return donorId; }
    public String getFoodName() { return foodName; }
    public FoodCategory getCategory() { return category; }
    public boolean isVegetarian() { return vegetarian; }
    public int getServings() { return servings; }
    public int getRemainingServings() { return remainingServings; }
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public int getShelfLifeMinutes() { return shelfLifeMinutes; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public BatchStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isExpiredAt(LocalDateTime when) {
        return !expiresAt.isAfter(when);
    }

    public long minutesRemaining(LocalDateTime when) {
        return Duration.between(when, expiresAt).toMinutes();
    }

    /** Share of the consumption window still left, from 1.0 (just cooked) to 0.0 (expired). */
    public double freshness(LocalDateTime when) {
        double left = Duration.between(when, expiresAt).toSeconds();
        return Math.max(0, Math.min(1, left / (shelfLifeMinutes * 60.0)));
    }

    /** Weight of {@code n} servings of this batch in kilograms. */
    public double kgFor(int n) {
        return n * category.getKgPerServing();
    }

    /** Reserve {@code n} servings for a dispatch. */
    public void allocate(int n) {
        if (status != BatchStatus.AVAILABLE) {
            throw new IllegalStateException("Batch " + id + " is " + status + ", not AVAILABLE");
        }
        if (n <= 0 || n > remainingServings) {
            throw new IllegalArgumentException("Cannot allocate " + n + " of " + remainingServings);
        }
        remainingServings -= n;
        if (remainingServings == 0) {
            status = BatchStatus.ALLOCATED;
        }
    }

    /** Return {@code n} servings to the pool (a dispatch was cancelled before pickup). */
    public void release(int n) {
        if (!status.isOpen()) {
            return; // an expired or withdrawn batch never comes back
        }
        if (n <= 0 || remainingServings + n > servings) {
            throw new IllegalArgumentException("Cannot release " + n);
        }
        remainingServings += n;
        status = BatchStatus.AVAILABLE;
    }

    public void markCompleted() {
        if (status == BatchStatus.ALLOCATED && remainingServings == 0) {
            status = BatchStatus.COMPLETED;
        }
    }

    /** @param wastedServings servings that will now never be eaten */
    public void markExpired(int wastedServings) {
        if (wastedServings < 0 || wastedServings > servings) {
            throw new IllegalArgumentException("wasted servings out of range");
        }
        remainingServings = wastedServings;
        status = BatchStatus.EXPIRED;
    }

    public void markWithdrawn() {
        status = BatchStatus.WITHDRAWN;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("donorId", donorId);
        m.put("foodName", foodName);
        m.put("category", category);
        m.put("vegetarian", vegetarian);
        m.put("servings", servings);
        m.put("remainingServings", remainingServings);
        m.put("preparedAt", preparedAt);
        m.put("shelfLifeMinutes", shelfLifeMinutes);
        m.put("expiresAt", expiresAt);
        m.put("status", status);
        m.put("notes", notes);
        m.put("createdAt", createdAt);
        return m;
    }

    @Override
    public String toString() {
        return id + " " + foodName + " (" + remainingServings + "/" + servings + ", " + status + ")";
    }
}
