package com.sevasync.storage;

import com.sevasync.model.Recipient;
import com.sevasync.model.Role;

/** Recipient organisations, read from the shared users table. */
public class SqliteRecipientRepository extends RoleRepository<Recipient> {

    public SqliteRecipientRepository(SqliteUserRepository users) {
        super(users, Role.RECIPIENT, Recipient.class);
    }
}
