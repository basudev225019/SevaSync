package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.security.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Optional;

/** Password hashes, stored apart from user profiles. */
public class CredentialRepository {
    private final DatabaseManager db;

    public CredentialRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<PasswordHasher.Hash> find(String userId) {
        return db.queryOne("SELECT hash, salt, iterations FROM credentials WHERE user_id = ?",
                rs -> new PasswordHasher.Hash(rs.getString(1), rs.getString(2), rs.getInt(3)), userId);
    }

    public void save(String userId, PasswordHasher.Hash hash, LocalDateTime now) {
        db.update("INSERT INTO credentials (user_id, hash, salt, iterations, updated_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(user_id) DO UPDATE SET hash = excluded.hash, salt = excluded.salt, "
                        + "iterations = excluded.iterations, updated_at = excluded.updated_at",
                userId, hash.hash(), hash.salt(), hash.iterations(), now);
    }
}
