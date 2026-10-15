package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** An orphanage, shelter, NGO ... that receives surplus food, up to a daily capacity. */
public class Recipient extends LocatedUser {
    public static final int MAX_DAILY_CAPACITY = 5000;

    private RecipientType type;
    private NeedLevel needLevel;
    private int dailyCapacity;

    public Recipient(String id, String name, String email, String phone, String address, GeoPoint location,
                     RecipientType type, NeedLevel needLevel, int dailyCapacity, AccountStatus status,
                     LocalDateTime createdAt) {
        super(id, name, email, phone, address, location, status, createdAt);
        this.type = Objects.requireNonNull(type, "type");
        this.needLevel = Objects.requireNonNull(needLevel, "needLevel");
        setDailyCapacity(dailyCapacity);
    }

    public RecipientType getType() {
        return type;
    }

    public void setType(RecipientType type) {
        this.type = Objects.requireNonNull(type);
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

    public final void setDailyCapacity(int dailyCapacity) {
        if (dailyCapacity <= 0 || dailyCapacity > MAX_DAILY_CAPACITY) {
            throw new IllegalArgumentException("dailyCapacity must be between 1 and " + MAX_DAILY_CAPACITY);
        }
        this.dailyCapacity = dailyCapacity;
    }

    @Override
    public Role getRole() {
        return Role.RECIPIENT;
    }

    @Override
    public String describe() {
        return super.describe() + " [" + type + ", need=" + needLevel + ", capacity=" + dailyCapacity + "/day]";
    }

    @Override
    protected void writeProfile(Map<String, Object> m) {
        super.writeProfile(m);
        m.put("recipientType", type);
        m.put("needLevel", needLevel);
        m.put("dailyCapacity", dailyCapacity);
    }

    @Override
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = super.toPublicMap();
        m.put("recipientType", type);
        m.put("needLevel", needLevel);
        m.put("dailyCapacity", dailyCapacity);
        return m;
    }
}
