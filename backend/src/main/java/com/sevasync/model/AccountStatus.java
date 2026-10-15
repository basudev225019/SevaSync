package com.sevasync.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Account life-cycle. Organisations (donors and recipients) start PENDING until an administrator verifies them;
 * volunteers are active straight away.
 */
public enum AccountStatus {
    PENDING {
        @Override
        public Set<Permission> filter(Set<Permission> rolePermissions) {
            // a pending organisation may only look after its own profile until it is verified
            Set<Permission> allowed = EnumSet.noneOf(Permission.class);
            if (rolePermissions.contains(Permission.UPDATE_OWN_PROFILE)) {
                allowed.add(Permission.UPDATE_OWN_PROFILE);
            }
            return allowed;
        }
    },
    ACTIVE {
        @Override
        public Set<Permission> filter(Set<Permission> rolePermissions) {
            return rolePermissions;
        }
    },
    SUSPENDED {
        @Override
        public Set<Permission> filter(Set<Permission> rolePermissions) {
            return EnumSet.noneOf(Permission.class);
        }
    };

    /** Narrows the role's permissions to what an account in this status may actually do. */
    public abstract Set<Permission> filter(Set<Permission> rolePermissions);

    public boolean canSignIn() {
        return this != SUSPENDED;
    }
}
