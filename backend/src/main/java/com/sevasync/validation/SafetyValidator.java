package com.sevasync.validation;

import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Enforces freshness thresholds and consumption windows. A batch that fails never enters the distribution cycle,
 * and food that expires on the way is never handed over.
 */
public class SafetyValidator {
    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 5000;
    /** A batch must still be fit for consumption for at least this long when it is logged. */
    public static final int MIN_REMAINING_MINUTES = 30;
    /** Matching ignores batches that expire sooner than this. */
    public static final int MIN_DISPATCH_MINUTES = 10;
    private static final int FUTURE_TOLERANCE_MINUTES = 5;

    private final Clock clock;

    public SafetyValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateNewBatch(FoodCategory category, int servings, LocalDateTime preparedAt, int shelfLifeMinutes)
            throws InvalidInputException, ExpiredBatchException, UnsafeFoodException {
        if (category == null || preparedAt == null) {
            throw new InvalidInputException("Category and preparation time are required");
        }
        if (servings < MIN_SERVINGS || servings > MAX_SERVINGS) {
            throw new InvalidInputException("Servings must be between " + MIN_SERVINGS + " and " + MAX_SERVINGS);
        }
        if (shelfLifeMinutes <= 0) {
            throw new InvalidInputException("Consumption window must be positive");
        }
        LocalDateTime now = TimeUtil.now(clock);
        if (preparedAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw new InvalidInputException("Preparation time cannot be in the future");
        }
        if (shelfLifeMinutes > category.getMaxShelfLifeMinutes()) {
            throw new UnsafeFoodException(category + " may be kept for at most "
                    + hours(category.getMaxShelfLifeMinutes()) + " h, but " + hours(shelfLifeMinutes)
                    + " h was declared");
        }
        LocalDateTime expiresAt = preparedAt.plusMinutes(shelfLifeMinutes);
        if (!expiresAt.isAfter(now)) {
            throw new ExpiredBatchException("This food already expired at " + expiresAt.toLocalTime());
        }
        long minutesLeft = Duration.between(now, expiresAt).toMinutes();
        if (minutesLeft < MIN_REMAINING_MINUTES) {
            throw new UnsafeFoodException("Only " + minutesLeft + " min of safe consumption time left; at least "
                    + MIN_REMAINING_MINUTES + " min are needed to redistribute it");
        }
    }

    public boolean isExpired(FoodBatch batch) {
        return batch.isExpiredAt(TimeUtil.now(clock));
    }

    public void requireFresh(FoodBatch batch) throws ExpiredBatchException {
        if (isExpired(batch)) {
            throw new ExpiredBatchException("Batch " + batch.getId() + " (" + batch.getFoodName() + ") expired at "
                    + batch.getExpiresAt().toLocalTime());
        }
    }

    private static String hours(int minutes) {
        double h = minutes / 60.0;
        return h == Math.rint(h) ? String.valueOf((int) h) : String.format("%.1f", h);
    }
}
