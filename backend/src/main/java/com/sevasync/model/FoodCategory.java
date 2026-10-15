package com.sevasync.model;

/**
 * Food categories with their maximum safe consumption window (minutes after preparation) and an average weight per
 * serving used for the impact figures.
 */
public enum FoodCategory {
    COOKED_MEAL(240, 0.35),
    BEVERAGE(480, 0.25),
    BAKERY(720, 0.15),
    FRUITS_VEGETABLES(1440, 0.20),
    PACKAGED(2880, 0.20);

    private final int maxShelfLifeMinutes;
    private final double kgPerServing;

    FoodCategory(int maxShelfLifeMinutes, double kgPerServing) {
        this.maxShelfLifeMinutes = maxShelfLifeMinutes;
        this.kgPerServing = kgPerServing;
    }

    public int getMaxShelfLifeMinutes() {
        return maxShelfLifeMinutes;
    }

    public double getKgPerServing() {
        return kgPerServing;
    }
}
