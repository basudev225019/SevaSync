package com.sevasync.validation;

import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Enforces freshness thresholds and consumption windows. A batch that fails never enters
 * the distribution cycle.
 */
public class SafetyValidator {
    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 5000;
    /** A batch must still be fit for consumption for at least this long when it is logged. */
    public static final int MIN_REMAINING_MINUTES = 30;
    private static final int FUTURE_TOLERANCE_MINUTES = 5;

    private final Clock clock;

    public SafetyValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateNewBatch(FoodCategory category, int servings, LocalDateTime preparedAt, int shelfLifeMinutes)
            throws InvalidInputException, ExpiredBatchException, UnsafeFoodException {
        if (category == null || preparedAt == null) {
            throw new InvalidInputException("category and preparation time are required");
        }
        if (servings < MIN_SERVINGS || servings > MAX_SERVINGS) {
            throw new InvalidInputException("servings must be between " + MIN_SERVINGS + " and " + MAX_SERVINGS);
        }
        if (shelfLifeMinutes <= 0) {
            throw new InvalidInputException("consumption window must be positive");
        }
        LocalDateTime now = TimeUtil.now(clock);
        if (preparedAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw new InvalidInputException("preparation time cannot be in the future");
        }
        if (shelfLifeMinutes > category.getMaxShelfLifeMinutes()) {
            throw new UnsafeFoodException(category + " may be kept for at most "
                    + category.getMaxShelfLifeMinutes() / 60.0 + " h, but " + shelfLifeMinutes / 60.0 + " h was declared");
        }
        LocalDateTime expiresAt = preparedAt.plusMinutes(shelfLifeMinutes);
        if (!expiresAt.isAfter(now)) {
            throw new ExpiredBatchException("Batch expired at " + expiresAt);
        }
        long minutesLeft = java.time.Duration.between(now, expiresAt).toMinutes();
        if (minutesLeft < MIN_REMAINING_MINUTES) {
            throw new UnsafeFoodException("Only " + minutesLeft + " min of safe consumption time left; at least "
                    + MIN_REMAINING_MINUTES + " min are needed to redistribute");
        }
    }

    public boolean isExpired(FoodBatch batch) {
        return batch.isExpiredAt(TimeUtil.now(clock));
    }

    public void validateForDispatch(FoodBatch batch) throws ExpiredBatchException {
        if (isExpired(batch)) {
            throw new ExpiredBatchException("Batch " + batch.getId() + " expired at " + batch.getExpiresAt());
        }
    }
}
