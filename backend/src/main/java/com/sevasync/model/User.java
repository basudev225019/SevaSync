package com.sevasync.model;

import com.sevasync.contract.Entity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract base of every person or organisation that signs in to SevaSync.
 *
 * <ul>
 *   <li><b>Abstraction</b> - {@link #getRole()} and {@link #writeProfile(Map)} are left to the subclasses.</li>
 *   <li><b>Encapsulation</b> - all state is private; changes go through validating setters.</li>
 *   <li><b>Inheritance</b> - {@link Admin}, {@link Donor}, {@link Recipient} and {@link Volunteer}.</li>
 *   <li><b>Polymorphism</b> - {@link #toMap()} and {@link #describe()} behave differently per subclass.</li>
 * </ul>
 * Credentials are deliberately <i>not</i> part of this class, so a password hash can never leak through a
 * serialised user.
 */
public abstract class User implements Entity {
    private final String id;
    private String name;
    private final String email;
    private String phone;
    private String address;
    private AccountStatus status;
    private final LocalDateTime createdAt;

    protected User(String id, String name, String email, String phone, String address, AccountStatus status,
                   LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.phone = phone == null ? "" : phone;
        this.address = address == null ? "" : address;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Which kind of participant this is. */
    public abstract Role getRole();

    /** Adds the subclass-specific fields to the JSON view (template-method hook used by {@link #toMap()}). */
    protected abstract void writeProfile(Map<String, Object> m);

    /** One-line human description, refined by each subclass. */
    public String describe() {
        return getRole().getDisplayName() + " " + id + ": " + name;
    }

    /** Effective permissions: what the role grants, narrowed by the account status. */
    public Set<Permission> permissions() {
        return status.filter(getRole().getPermissions());
    }

    public boolean can(Permission permission) {
        return permissions().contains(permission);
    }

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

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public final Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("role", getRole());
        m.put("name", name);
        m.put("email", email);
        m.put("phone", phone);
        m.put("address", address);
        m.put("status", status);
        m.put("createdAt", createdAt);
        writeProfile(m);
        m.put("permissions", permissions());
        return m;
    }

    /** Minimal view that is safe to show other participants (no contact details). */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("role", getRole());
        m.put("name", name);
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof User u && u.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return describe();
    }
}
