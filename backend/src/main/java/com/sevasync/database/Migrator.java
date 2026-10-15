package com.sevasync.database;

import com.sevasync.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Versioned schema migrations. Scripts live in {@code src/main/resources/db/migration} and are applied in order,
 * each one exactly once, with the version recorded in {@code schema_version}.
 */
public final class Migrator {
    private static final Logger LOG = LoggerFactory.getLogger(Migrator.class);

    /** Ordered list of migration scripts. Append new ones; never edit a released script. */
    public static final List<String> SCRIPTS = List.of(
            "V1__core_schema.sql",
            "V2__indexes.sql"
    );

    private Migrator() {
    }

    /**
     * Opens {@code dbFile}, moving a database written by SevaSync v1 (no {@code schema_version} table) aside first,
     * and brings the schema up to date.
     */
    public static DatabaseManager openAndMigrate(Path dbFile) {
        if (Files.exists(dbFile) && isLegacy(dbFile)) {
            Path backup = dbFile.resolveSibling("sevasync-v1-legacy-" + System.currentTimeMillis() + ".db");
            try {
                Files.move(dbFile, backup, StandardCopyOption.REPLACE_EXISTING);
                for (String suffix : new String[]{"-wal", "-shm"}) {
                    Path side = dbFile.resolveSibling(dbFile.getFileName() + suffix);
                    if (Files.exists(side)) {
                        Files.move(side, backup.resolveSibling(backup.getFileName() + suffix),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                LOG.warn("Found a SevaSync v1 database; it was kept as {} and a fresh v2 database was created",
                        backup.getFileName());
            } catch (IOException e) {
                throw new StorageException("Cannot move legacy database aside: " + e.getMessage(), e);
            }
        }
        DatabaseManager db = DatabaseManager.forFile(dbFile);
        migrate(db);
        return db;
    }

    /** Applies every pending migration. Safe to call on every start-up. */
    public static int migrate(DatabaseManager db) {
        db.executeScript("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, script TEXT NOT NULL,"
                + " applied_at TEXT NOT NULL)");
        long current = db.queryLong("SELECT COALESCE(MAX(version), 0) FROM schema_version");
        int applied = 0;
        for (int i = (int) current; i < SCRIPTS.size(); i++) {
            String script = SCRIPTS.get(i);
            String sql = load(script);
            int version = i + 1;
            db.runInTransaction(() -> {
                db.executeScript(sql);
                db.update("INSERT INTO schema_version (version, script, applied_at) VALUES (?, ?, ?)",
                        version, script, LocalDateTime.now().toString());
            });
            applied++;
            LOG.info("Applied migration {}", script);
        }
        return applied;
    }

    public static long currentVersion(DatabaseManager db) {
        return db.queryLong("SELECT COALESCE(MAX(version), 0) FROM schema_version");
    }

    private static boolean isLegacy(Path dbFile) {
        DatabaseManager probe = DatabaseManager.forFile(dbFile);
        try {
            boolean hasV1Tables = probe.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'donors'") > 0;
            boolean versioned = probe.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'") > 0;
            return hasV1Tables && !versioned;
        } finally {
            probe.close();
        }
    }

    private static String load(String script) {
        String resource = "/db/migration/" + script;
        try (InputStream in = Migrator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new StorageException("Missing migration " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Cannot read migration " + resource, e);
        }
    }
}
