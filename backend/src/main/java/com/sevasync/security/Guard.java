package com.sevasync.security;

import com.sevasync.exception.AccessDeniedException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Permission;
import com.sevasync.model.User;

/** One-line permission checks for services ({@code Guard.require(user, Permission.LOG_BATCH)}). */
public final class Guard {
    private Guard() {
    }

    public static void require(User user, Permission permission) throws AccessDeniedException {
        if (user == null) {
            throw new AccessDeniedException("Sign in required");
        }
        if (!user.can(permission)) {
            if (user.getStatus() == AccountStatus.PENDING) {
                throw new AccessDeniedException("Your account is awaiting verification by an administrator");
            }
            throw new AccessDeniedException("Your role (" + user.getRole() + ") is not allowed to do this");
        }
    }

    /** The user must be acting on their own record (or hold {@code override}, e.g. an administrator). */
    public static void requireSelfOr(User user, String ownerId, Permission override) throws AccessDeniedException {
        if (user != null && user.getId().equals(ownerId)) {
            return;
        }
        if (user != null && user.can(override)) {
            return;
        }
        throw new AccessDeniedException("You can only act on your own records");
    }
}
