package com.sevasync.storage;

import com.sevasync.model.AccountStatus;
import com.sevasync.model.Role;
import com.sevasync.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Typed view over {@link SqliteUserRepository} for one role (Adapter): a {@code Repository<Donor>} that really
 * reads the shared users table. Lets services ask for "all recipients" and get {@code Recipient} objects back.
 *
 * @param <T> the {@link User} subclass this role maps to
 */
public class RoleRepository<T extends User> implements Repository<T> {
    private final SqliteUserRepository users;
    private final Role role;
    private final Class<T> type;

    public RoleRepository(SqliteUserRepository users, Role role, Class<T> type) {
        this.users = users;
        this.role = role;
        this.type = type;
    }

    @Override
    public Optional<T> findById(String id) {
        return users.findById(id).filter(type::isInstance).map(type::cast);
    }

    @Override
    public List<T> findAll() {
        return users.findByRole(role).stream().map(type::cast).toList();
    }

    /** Participants of this role whose account is active (the only ones that take part in matching). */
    public List<T> findActive() {
        return users.findByRoleAndStatus(role, AccountStatus.ACTIVE).stream().map(type::cast).toList();
    }

    @Override
    public long count() {
        return users.countByRole(role);
    }

    @Override
    public void save(T entity) {
        users.save(entity);
    }
}
