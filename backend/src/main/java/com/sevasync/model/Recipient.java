package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** An orphanage, shelter, NGO ... that receives surplus food. */
public class Recipient extends User {
    private static final long serialVersionUID = 1L;

    public static final String[] CSV_HEADER = {"id", "name", "phone", "email", "address", "recipientType",
            "needLevel", "dailyCapacity", "createdAt"};

    private final RecipientType type;
    private NeedLevel needLevel;
    private int dailyCapacity;

    public Recipient(String id, String name, String phone, String email, String address,
                     RecipientType type, NeedLevel needLevel, int dailyCapacity, LocalDateTime createdAt) {
        super(id, name, phone, email, address, createdAt);
        this.type = Objects.requireNonNull(type, "type");
        this.needLevel = Objects.requireNonNull(needLevel, "needLevel");
        if (dailyCapacity <= 0) {
            throw new IllegalArgumentException("dailyCapacity must be positive");
        }
        this.dailyCapacity = dailyCapacity;
    }

    public RecipientType getType() {
        return type;
    }

    public NeedLevel getNeedLevel() {
        return needLevel;
    }

    public void setNeedLevel(NeedLevel needLevel) {
        this.needLevel = Objects.requireNonNull(needLevel);
    }

    public int getDailyCapacity() {
        return dailyCapacity;
    }

    public void setDailyCapacity(int dailyCapacity) {
        if (dailyCapacity <= 0) {
            throw new IllegalArgumentException("dailyCapacity must be positive");
        }
        this.dailyCapacity = dailyCapacity;
    }

    @Override
    public String getRole() {
        return "RECIPIENT";
    }

    @Override
    public String describe() {
        return super.describe() + " [" + type + ", need=" + needLevel + ", capacity=" + dailyCapacity + "/day]";
    }

    @Override
    public String[] toCsvRow() {
        return new String[]{getId(), getName(), getPhone(), getEmail(), getAddress(), type.name(),
                needLevel.name(), String.valueOf(dailyCapacity), getCreatedAt().toString()};
    }

    public static Recipient fromCsv(String[] f) {
        return new Recipient(f[0], f[1], f[2], f[3], f[4], RecipientType.valueOf(f[5]),
                NeedLevel.valueOf(f[6]), Integer.parseInt(f[7]), LocalDateTime.parse(f[8]));
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = baseMap();
        m.put("recipientType", type);
        m.put("needLevel", needLevel);
        m.put("dailyCapacity", dailyCapacity);
        return m;
    }
}
