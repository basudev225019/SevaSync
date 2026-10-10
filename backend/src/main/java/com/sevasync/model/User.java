package com.sevasync.model;

import com.sevasync.contract.Entity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base of every person/organisation that takes part in SevaSync.
 * Demonstrates abstraction (abstract {@link #getRole()}), encapsulation (private fields)
 * and inheritance ({@link Donor}, {@link Recipient}).
 */
public abstract class User implements Entity {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private final LocalDateTime createdAt;

    protected User(String id, String name, String phone, String email, String address, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.phone = Objects.requireNonNull(phone, "phone");
        this.email = email == null ? "" : email;
        this.address = address == null ? "" : address;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Role label, e.g. DONOR or RECIPIENT (polymorphic). */
    public abstract String getRole();

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = Objects.requireNonNull(phone);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String describe() {
        return getRole() + " " + id + ": " + name + " (" + phone + ")";
    }

    protected Map<String, Object> baseMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("role", getRole());
        m.put("name", name);
        m.put("phone", phone);
        m.put("email", email);
        m.put("address", address);
        m.put("createdAt", createdAt);
        return m;
    }

    @Override
    public String toString() {
        return describe();
    }
}
