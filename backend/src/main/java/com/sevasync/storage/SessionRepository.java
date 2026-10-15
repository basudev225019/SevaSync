package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;

import java.time.LocalDateTime;
import java.util.Optional;

/** Server-side login sessions, keyed by the SHA-256 of the bearer token. */
public class SessionRepository {

    public record Session(String tokenHash, String userId, LocalDateTime createdAt, LocalDateTime expiresAt) {
    }

    private final DatabaseManager db;

    public SessionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void create(Session s) {
        db.update("INSERT INTO sessions (token_hash, user_id, created_at, expires_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
                s.tokenHash(), s.userId(), s.createdAt(), s.expiresAt(), s.createdAt());
    }

    public Optional<Session> find(String tokenHash) {
        return db.queryOne("SELECT token_hash, user_id, created_at, expires_at FROM sessions WHERE token_hash = ?",
                rs -> new Session(rs.getString(1), rs.getString(2), LocalDateTime.parse(rs.getString(3)),
                        LocalDateTime.parse(rs.getString(4))), tokenHash);
    }

    public void touch(String tokenHash, LocalDateTime now) {
        db.update("UPDATE sessions SET last_seen_at = ? WHERE token_hash = ?", now, tokenHash);
    }

    public void delete(String tokenHash) {
        db.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
    }

    public int deleteAllForUser(String userId) {
        return db.update("DELETE FROM sessions WHERE user_id = ?", userId);
    }

    public int deleteExpired(LocalDateTime now) {
        return db.update("DELETE FROM sessions WHERE expires_at <= ?", now);
    }
}
