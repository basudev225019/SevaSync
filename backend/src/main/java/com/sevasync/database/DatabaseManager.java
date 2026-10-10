package com.sevasync.database;

import com.sevasync.exception.StorageException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Manages SQLite database connections, executes initial schema creation,
 * and configures database pragmas (WAL mode, foreign keys).
 */
public class DatabaseManager implements AutoCloseable {
    private final String jdbcUrl;
    private Connection persistentConnection; // For memory mode or keeping connection pool alive

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initDriver();
        if (jdbcUrl.contains(":memory:")) {
            try {
                this.persistentConnection = DriverManager.getConnection(jdbcUrl);
            } catch (SQLException e) {
                throw new StorageException("Failed to open in-memory SQLite connection: " + e.getMessage(), e);
            }
        }
        initSchema();
    }

    public static DatabaseManager forFile(Path dbPath) {
        try {
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (Exception e) {
            throw new StorageException("Failed to create directory for database: " + e.getMessage(), e);
        }
        return new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public static DatabaseManager inMemory() {
        return new DatabaseManager("jdbc:sqlite::memory:");
    }

    private static void initDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new StorageException("SQLite JDBC driver not found on classpath", e);
        }
    }

    /**
     * Obtains an active connection to the SQLite database.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (persistentConnection != null && !persistentConnection.isClosed()) {
            return persistentConnection;
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            if (!jdbcUrl.contains(":memory:")) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }
        }
        return conn;
    }

    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            if (!jdbcUrl.contains(":memory:")) {
                stmt.execute("PRAGMA journal_mode = WAL;");
            }

            String schemaSql = loadSchemaSql();
            for (String statement : schemaSql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to initialize SQLite schema: " + e.getMessage(), e);
        }
    }

    private String loadSchemaSql() {
        try (InputStream is = getClass().getResourceAsStream("/schema.sql")) {
            if (is == null) {
                return fallbackSchema();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return fallbackSchema();
        }
    }

    private static String fallbackSchema() {
        return """
            CREATE TABLE IF NOT EXISTS donors (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                address TEXT,
                donor_type TEXT NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS recipients (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                address TEXT,
                recipient_type TEXT NOT NULL,
                need_level TEXT NOT NULL,
                daily_capacity INTEGER NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS food_batches (
                id TEXT PRIMARY KEY,
                donor_id TEXT NOT NULL,
                food_name TEXT NOT NULL,
                category TEXT NOT NULL,
                vegetarian INTEGER NOT NULL DEFAULT 0,
                servings INTEGER NOT NULL,
                remaining_servings INTEGER NOT NULL,
                prepared_at TEXT NOT NULL,
                shelf_life_minutes INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (donor_id) REFERENCES donors(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS dispatches (
                id TEXT PRIMARY KEY,
                batch_id TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                servings INTEGER NOT NULL,
                status TEXT NOT NULL,
                assigned_at TEXT NOT NULL,
                delivered_at TEXT,
                volunteer_name TEXT,
                note TEXT,
                FOREIGN KEY (batch_id) REFERENCES food_batches(id) ON DELETE CASCADE,
                FOREIGN KEY (recipient_id) REFERENCES recipients(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS audit_log (
                seq INTEGER PRIMARY KEY,
                timestamp TEXT NOT NULL,
                action TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                details TEXT,
                prev_hash TEXT NOT NULL,
                hash TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_batches_status ON food_batches(status);
            CREATE INDEX IF NOT EXISTS idx_dispatches_status ON dispatches(status);
        """;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public synchronized void close() {
        if (persistentConnection != null) {
            try {
                if (!persistentConnection.isClosed()) {
                    persistentConnection.close();
                }
            } catch (SQLException ignored) {
            }
        }
    }
}
