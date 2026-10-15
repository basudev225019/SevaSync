package com.sevasync.storage;

import com.sevasync.model.Donor;
import com.sevasync.model.Role;

/** Donors, read from the shared users table. */
public class SqliteDonorRepository extends RoleRepository<Donor> {

    public SqliteDonorRepository(SqliteUserRepository users) {
        super(users, Role.DONOR, Donor.class);
    }
}
