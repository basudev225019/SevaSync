package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** A student who picks food up from donors and carries it to recipients. */
public class Volunteer extends LocatedUser {
    /** How many pickups one volunteer may hold at the same time. */
    public static final int MAX_ACTIVE_DISPATCHES = 3;

    private VehicleType vehicle;

    public Volunteer(String id, String name, String email, String phone, String address, GeoPoint location,
                     VehicleType vehicle, AccountStatus status, LocalDateTime createdAt) {
        super(id, name, email, phone, address, location, status, createdAt);
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    }

    public VehicleType getVehicle() {
        return vehicle;
    }

    public void setVehicle(VehicleType vehicle) {
        this.vehicle = Objects.requireNonNull(vehicle);
    }

    /** Whether this volunteer's vehicle can carry {@code servings} in one trip. */
    public boolean canCarry(int servings) {
        return servings <= vehicle.getMaxServings();
    }

    @Override
    public Role getRole() {
        return Role.VOLUNTEER;
    }

    @Override
    public String describe() {
        return super.describe() + " [" + vehicle + "]";
    }

    @Override
    protected void writeProfile(Map<String, Object> m) {
        super.writeProfile(m);
        m.put("vehicle", vehicle);
    }

    @Override
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = super.toPublicMap();
        // volunteers are private individuals: other participants only see a first name
        String first = getName().split(" ")[0];
        m.put("name", first);
        m.put("vehicle", vehicle);
        return m;
    }
}
