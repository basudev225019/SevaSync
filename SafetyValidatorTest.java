package com.sevasync;

import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.FoodCategory;
import com.sevasync.validation.SafetyValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafetyValidatorTest {
    private final TestClock clock = new TestClock(Instant.parse("2026-10-09T12:00:00Z"));
    private final SafetyValidator validator = new SafetyValidator(clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);

    @Test
    void acceptsFreshBatch() {
        assertDoesNotThrow(() ->
                validator.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusMinutes(30), 180));
    }

    @Test
    void rejectsExpiredBatch() {
        assertThrows(ExpiredBatchException.class, () ->
                validator.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusHours(5), 180));
    }

    @Test
    void rejectsShelfLifeAboveCategoryLimit() {
        assertThrows(UnsafeFoodException.class, () ->
                validator.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now, 6 * 60));
    }

    @Test
    void rejectsBatchWithTooLittleTimeLeft() {
        assertThrows(UnsafeFoodException.class, () ->
                validator.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusMinutes(170), 180));
    }

    @Test
    void rejectsBadQuantitiesAndFutureTimes() {
        assertThrows(InvalidInputException.class, () ->
                validator.validateNewBatch(FoodCategory.BAKERY, 0, now, 120));
        assertThrows(InvalidInputException.class, () ->
                validator.validateNewBatch(FoodCategory.BAKERY, 5001, now, 120));
        assertThrows(InvalidInputException.class, () ->
                validator.validateNewBatch(FoodCategory.BAKERY, 10, now, 0));
        assertThrows(InvalidInputException.class, () ->
                validator.validateNewBatch(FoodCategory.BAKERY, 10, now.plusHours(2), 120));
    }
}
