package com.sevasync.model;

/** Food categories with the maximum safe consumption window (minutes after preparation). */
public enum FoodCategory {
    COOKED_MEAL(240),
    BEVERAGE(480),
    BAKERY(720),
    FRUITS_VEGETABLES(1440),
    PACKAGED(2880);

    private final int maxShelfLifeMinutes;

    FoodCategory(int maxShelfLifeMinutes) {
        this.maxShelfLifeMinutes = maxShelfLifeMinutes;
    }

    public int getMaxShelfLifeMinutes() {
        return maxShelfLifeMinutes;
    }
}
