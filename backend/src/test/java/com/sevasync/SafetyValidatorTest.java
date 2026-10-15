package com.sevasync;

import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.FoodCategory;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafetyValidatorTest {
    private final TestClock clock = new TestClock(Instant.parse("2026-10-09T10:00:00Z"));
    private final SafetyValidator v = new SafetyValidator(clock);
    private final LocalDateTime now = TimeUtil.now(clock);

    @Test
    void acceptsFreshFoodWithinItsCategoryWindow() {
        assertDoesNotThrow(() -> v.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusMinutes(30), 240));
        assertDoesNotThrow(() -> v.validateNewBatch(FoodCategory.PACKAGED, 10, now.minusHours(10), 2880));
    }

    @Test
    void rejectsShelfLifeLongerThanTheCategoryAllows() {
        assertThrows(UnsafeFoodException.class,
                () -> v.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now, 241));
    }

    @Test
    void rejectsExpiredOrNearlyExpiredFood() {
        assertThrows(ExpiredBatchException.class,
                () -> v.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusMinutes(240), 240));
        assertThrows(UnsafeFoodException.class,
                () -> v.validateNewBatch(FoodCategory.COOKED_MEAL, 50, now.minusMinutes(215), 240));
    }

    @Test
    void rejectsImpossibleQuantitiesAndFutureCooking() {
        assertThrows(InvalidInputException.class, () -> v.validateNewBatch(FoodCategory.BAKERY, 0, now, 60));
        assertThrows(InvalidInputException.class, () -> v.validateNewBatch(FoodCategory.BAKERY, 5001, now, 60));
        assertThrows(InvalidInputException.class,
                () -> v.validateNewBatch(FoodCategory.BAKERY, 10, now.plusMinutes(30), 60));
        assertThrows(InvalidInputException.class, () -> v.validateNewBatch(null, 10, now, 60));
    }
}
