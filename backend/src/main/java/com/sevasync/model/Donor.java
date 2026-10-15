package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** A hostel mess, canteen or event coordinator that logs surplus food. */
public class Donor extends LocatedUser {
    private DonorType type;

    public Donor(String id, String name, String email, String phone, String address, GeoPoint location,
                 DonorType type, AccountStatus status, LocalDateTime createdAt) {
        super(id, name, email, phone, address, location, status, createdAt);
        this.type = Objects.requireNonNull(type, "type");
    }

    public DonorType getType() {
        return type;
    }

    public void setType(DonorType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public Role getRole() {
        return Role.DONOR;
    }

    @Override
    public String describe() {
        return super.describe() + " [" + type + "]";
    }

    @Override
    protected void writeProfile(Map<String, Object> m) {
        super.writeProfile(m);
        m.put("donorType", type);
    }

    @Override
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = super.toPublicMap();
        m.put("donorType", type);
        return m;
    }
}
