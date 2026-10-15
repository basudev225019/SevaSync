package com.sevasync.model;

/** How urgently a recipient needs food. Higher weight is served first by the matching strategies. */
public enum NeedLevel {
    NORMAL(1), HIGH(2), CRITICAL(3);

    private final int weight;

    NeedLevel(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
