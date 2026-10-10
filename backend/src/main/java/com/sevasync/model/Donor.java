package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** A hostel mess, canteen or event coordinator that logs surplus food. */
public class Donor extends User {
    private static final long serialVersionUID = 1L;

    public static final String[] CSV_HEADER = {"id", "name", "phone", "email", "address", "donorType", "createdAt"};

    private final DonorType type;

    public Donor(String id, String name, String phone, String email, String address,
                 DonorType type, LocalDateTime createdAt) {
        super(id, name, phone, email, address, createdAt);
        this.type = Objects.requireNonNull(type, "type");
    }

    public DonorType getType() {
        return type;
    }

    @Override
    public String getRole() {
        return "DONOR";
    }

    @Override
    public String describe() {
        return super.describe() + " [" + type + "]";
    }

    @Override
    public String[] toCsvRow() {
        return new String[]{getId(), getName(), getPhone(), getEmail(), getAddress(),
                type.name(), getCreatedAt().toString()};
    }

    public static Donor fromCsv(String[] f) {
        return new Donor(f[0], f[1], f[2], f[3], f[4], DonorType.valueOf(f[5]), LocalDateTime.parse(f[6]));
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = baseMap();
        m.put("donorType", type);
        return m;
    }
}
