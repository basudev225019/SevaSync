package com.sevasync.storage;

import com.sevasync.model.Role;
import com.sevasync.model.Volunteer;

/** Volunteers, read from the shared users table. */
public class SqliteVolunteerRepository extends RoleRepository<Volunteer> {

    public SqliteVolunteerRepository(SqliteUserRepository users) {
        super(users, Role.VOLUNTEER, Volunteer.class);
    }
}
