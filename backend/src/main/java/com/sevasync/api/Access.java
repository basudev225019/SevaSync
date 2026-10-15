package com.sevasync.api;

import com.sevasync.model.Permission;

/**
 * Access rule attached to every route: open to anyone, any signed-in user, or users holding one permission.
 *
 * @param signedIn   whether a valid session is required
 * @param permission permission required on top of signing in, or {@code null}
 */
public record Access(boolean signedIn, Permission permission) {
    public static final Access PUBLIC = new Access(false, null);
    public static final Access SIGNED_IN = new Access(true, null);

    public static Access require(Permission p) {
        return new Access(true, p);
    }
}
