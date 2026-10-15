package com.sevasync.model;

/**
 * Fine-grained capabilities. Endpoints and services check a permission, never a role, so a role's powers can be
 * changed in one place ({@link Role}) without touching any business code.
 */
public enum Permission {
    // everyone with an active account
    VIEW_NETWORK,
    UPDATE_OWN_PROFILE,

    // donors
    LOG_BATCH,
    WITHDRAW_BATCH,
    VIEW_OWN_BATCHES,

    // volunteers
    VIEW_OPEN_PICKUPS,
    CLAIM_DISPATCH,
    PROGRESS_DISPATCH,

    // recipients
    VIEW_INCOMING,
    CONFIRM_RECEIPT,

    // administrators
    VIEW_ALL_BATCHES,
    VIEW_ALL_DISPATCHES,
    RUN_MATCHING,
    ASSIGN_MANUALLY,
    CANCEL_ANY_DISPATCH,
    MANAGE_USERS,
    VIEW_AUDIT,
    VIEW_REPORTS,
    RUN_BENCHMARK,
    MANAGE_SETTINGS,
    MANAGE_BACKUPS
}
