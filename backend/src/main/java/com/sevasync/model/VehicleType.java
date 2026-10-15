package com.sevasync.model;

/** How a volunteer travels, which decides speed and how many servings fit into one trip. */
public enum VehicleType {
    WALK(4.5, 25),
    BICYCLE(13, 45),
    SCOOTER(24, 90),
    CAR(28, 250);

    /** Fixed minutes for parking, loading and handing over, on top of travel time. */
    public static final int HANDLING_MINUTES = 8;

    private final double speedKmh;
    private final int maxServings;

    VehicleType(double speedKmh, int maxServings) {
        this.speedKmh = speedKmh;
        this.maxServings = maxServings;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public int getMaxServings() {
        return maxServings;
    }

    /** Whole minutes to cover {@code km} including handling time. */
    public int tripMinutes(double km) {
        return (int) Math.ceil(km / speedKmh * 60) + HANDLING_MINUTES;
    }
}
