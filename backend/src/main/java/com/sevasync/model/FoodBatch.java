package com.sevasync.model;

import com.sevasync.contract.Entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A batch of surplus food logged by a donor. The expiry time is derived, never entered directly. */
public class FoodBatch implements Entity {
    private static final long serialVersionUID = 1L;

    public static final String[] CSV_HEADER = {"id", "donorId", "foodName", "category", "vegetarian", "servings",
            "remainingServings", "preparedAt", "shelfLifeMinutes", "expiresAt", "status", "createdAt"};

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
    private final LocalDateTime createdAt;

    public FoodBatch(String id, String donorId, String foodName, FoodCategory category, boolean vegetarian,
                     int servings, int remainingServings, LocalDateTime preparedAt, int shelfLifeMinutes,
                     BatchStatus status, LocalDateTime createdAt) {
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
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStatus(BatchStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public void setRemainingServings(int remaining) {
        if (remaining < 0 || remaining > servings) {
            throw new IllegalArgumentException("remaining must be between 0 and " + servings);
        }
        this.remainingServings = remaining;
    }

    public boolean isExpiredAt(LocalDateTime when) {
        return !expiresAt.isAfter(when);
    }

    public long minutesRemaining(LocalDateTime when) {
        return Duration.between(when, expiresAt).toMinutes();
    }

    /** Reserve {@code n} servings for a dispatch. */
    public void allocate(int n) {
        if (n <= 0 || n > remainingServings) {
            throw new IllegalArgumentException("Cannot allocate " + n + " of " + remainingServings);
        }
        remainingServings -= n;
        if (remainingServings == 0) {
            status = BatchStatus.ALLOCATED;
        }
    }

    /** Return {@code n} servings to the pool (cancelled dispatch). */
    public void release(int n) {
        if (n <= 0 || remainingServings + n > servings) {
            throw new IllegalArgumentException("Cannot release " + n);
        }
        remainingServings += n;
        if (status == BatchStatus.ALLOCATED) {
            status = BatchStatus.AVAILABLE;
        }
    }

    @Override
    public String[] toCsvRow() {
        return new String[]{id, donorId, foodName, category.name(), String.valueOf(vegetarian),
                String.valueOf(servings), String.valueOf(remainingServings), preparedAt.toString(),
                String.valueOf(shelfLifeMinutes), expiresAt.toString(), status.name(), createdAt.toString()};
    }

    public static FoodBatch fromCsv(String[] f) {
        return new FoodBatch(f[0], f[1], f[2], FoodCategory.valueOf(f[3]), Boolean.parseBoolean(f[4]),
                Integer.parseInt(f[5]), Integer.parseInt(f[6]), LocalDateTime.parse(f[7]),
                Integer.parseInt(f[8]), BatchStatus.valueOf(f[10]), LocalDateTime.parse(f[11]));
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
        m.put("createdAt", createdAt);
        return m;
    }

    @Override
    public String toString() {
        return id + " " + foodName + " (" + remainingServings + "/" + servings + ", " + status + ")";
    }
}
