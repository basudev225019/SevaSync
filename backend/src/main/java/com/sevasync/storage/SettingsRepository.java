package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Small key/value store for settings an administrator can change at runtime. */
public class SettingsRepository {
    public static final String MATCHING_STRATEGY = "matching.strategy";
    public static final String AUTO_MATCH = "matching.auto";

    private final DatabaseManager db;

    public SettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<String> get(String key) {
        return db.queryOne("SELECT value FROM settings WHERE key = ?", rs -> rs.getString(1), key);
    }

    public String get(String key, String fallback) {
        return get(key).orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public void put(String key, String value) {
        db.update("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value);
    }

    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        db.query("SELECT key, value FROM settings ORDER BY key", rs -> m.put(rs.getString(1), rs.getString(2)));
        return m;
    }
}
