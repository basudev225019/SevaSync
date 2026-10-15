package com.sevasync.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.sevasync.model.Permission.*;

/** The four kinds of participant and what each of them is allowed to do. */
public enum Role {
    ADMIN("Administrator", "ADM", false, AccountStatus.ACTIVE,
            EnumSet.of(VIEW_NETWORK, UPDATE_OWN_PROFILE, VIEW_ALL_BATCHES, VIEW_ALL_DISPATCHES, RUN_MATCHING,
                    ASSIGN_MANUALLY, CANCEL_ANY_DISPATCH, MANAGE_USERS, VIEW_AUDIT, VIEW_REPORTS, RUN_BENCHMARK,
                    MANAGE_SETTINGS, MANAGE_BACKUPS)),
    DONOR("Food donor", "DON", true, AccountStatus.PENDING,
            EnumSet.of(VIEW_NETWORK, UPDATE_OWN_PROFILE, LOG_BATCH, WITHDRAW_BATCH, VIEW_OWN_BATCHES)),
    RECIPIENT("Recipient organisation", "REC", true, AccountStatus.PENDING,
            EnumSet.of(VIEW_NETWORK, UPDATE_OWN_PROFILE, VIEW_INCOMING, CONFIRM_RECEIPT)),
    VOLUNTEER("Volunteer", "VOL", true, AccountStatus.ACTIVE,
            EnumSet.of(VIEW_NETWORK, UPDATE_OWN_PROFILE, VIEW_OPEN_PICKUPS, CLAIM_DISPATCH, PROGRESS_DISPATCH));

    private final String displayName;
    private final String idPrefix;
    private final boolean selfRegistrable;
    private final AccountStatus initialStatus;
    private final Set<Permission> permissions;

    Role(String displayName, String idPrefix, boolean selfRegistrable, AccountStatus initialStatus,
         Set<Permission> permissions) {
        this.displayName = displayName;
        this.idPrefix = idPrefix;
        this.selfRegistrable = selfRegistrable;
        this.initialStatus = initialStatus;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIdPrefix() {
        return idPrefix;
    }

    /** Administrators are created by the system, never through the public sign-up form. */
    public boolean isSelfRegistrable() {
        return selfRegistrable;
    }

    public AccountStatus getInitialStatus() {
        return initialStatus;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
