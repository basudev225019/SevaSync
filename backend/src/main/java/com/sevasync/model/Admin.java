package com.sevasync.model;

import java.time.LocalDateTime;
import java.util.Map;

/** Platform coordinator: verifies organisations, runs matching and watches the audit trail. Has no map location. */
public class Admin extends User {

    public Admin(String id, String name, String email, String phone, AccountStatus status, LocalDateTime createdAt) {
        super(id, name, email, phone, "", status, createdAt);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    @Override
    protected void writeProfile(Map<String, Object> m) {
        // administrators carry no extra profile data
    }
}
