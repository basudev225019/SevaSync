package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.util.CsvUtil;
import com.sevasync.util.TimeUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Append-only tamper-evident audit trail.
 * Supports both SQLite database storage and CSV fallback.
 * Every row carries the SHA-256 hash of its content plus the previous row's hash.
 */
public class AuditLogger {
    public static final String[] HEADER = {"seq", "timestamp", "action", "entityType", "entityId", "details",
            "prevHash", "hash"};
    private static final String GENESIS = "0".repeat(64);

    public record AuditEntry(long seq, LocalDateTime timestamp, String action, String entityType,
                             String entityId, String details, String prevHash, String hash) {
    }

    public record Verification(boolean valid, int entries, long firstBadSeq) {
    }

    private final DatabaseManager db;
    private final FileStorageManager storage;
    private final Path file;
    private final Clock clock;
    private long lastSeq = 0;
    private String lastHash = GENESIS;

    /**
     * Constructs SQLite-backed AuditLogger.
     */
    public AuditLogger(DatabaseManager db, Clock clock) {
        this.db = db;
        this.storage = null;
        this.file = null;
        this.clock = clock;
        initFromDatabase();
    }

    /**
     * Constructs CSV-backed AuditLogger (fallback / file mode).
     */
    public AuditLogger(FileStorageManager storage, String fileName, Clock clock) {
        this.db = null;
        this.storage = storage;
        this.file = storage.resolve(fileName);
        this.clock = clock;
        List<String> lines = storage.readLines(file);
        if (lines.isEmpty()) {
            storage.appendLine(file, CsvUtil.join(HEADER));
        } else {
            List<AuditEntry> entries = parseCsv(lines);
            if (!entries.isEmpty()) {
                AuditEntry last = entries.get(entries.size() - 1);
                lastSeq = last.seq();
                lastHash = last.hash();
            }
        }
    }

    private void initFromDatabase() {
        String sql = "SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                lastSeq = rs.getLong("seq");
                lastHash = rs.getString("hash");
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to read last audit log entry from database: " + e.getMessage(), e);
        }
    }

    public synchronized AuditEntry log(String action, String entityType, String entityId, String details) {
        long seq = lastSeq + 1;
        LocalDateTime ts = TimeUtil.now(clock);
        String cleanDetails = details == null ? "" : details.replace('\r', ' ').replace('\n', ' ');
        String hash = computeHash(lastHash, seq, ts, action, entityType, entityId, cleanDetails);
        AuditEntry entry = new AuditEntry(seq, ts, action, entityType, entityId, cleanDetails, lastHash, hash);

        if (db != null) {
            String sql = "INSERT INTO audit_log (seq, timestamp, action, entity_type, entity_id, details, prev_hash, hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, seq);
                ps.setString(2, ts.toString());
                ps.setString(3, action);
                ps.setString(4, entityType);
                ps.setString(5, entityId);
                ps.setString(6, cleanDetails);
                ps.setString(7, lastHash);
                ps.setString(8, hash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to insert audit log entry into database: " + e.getMessage(), e);
            }
        } else if (storage != null && file != null) {
            storage.appendLine(file, CsvUtil.join(new String[]{String.valueOf(seq), ts.toString(), action, entityType,
                    entityId, cleanDetails, lastHash, hash}));
        }

        lastSeq = seq;
        lastHash = hash;
        return entry;
    }

    public synchronized List<AuditEntry> readAll() {
        if (db != null) {
            String sql = "SELECT seq, timestamp, action, entity_type, entity_id, details, prev_hash, hash FROM audit_log ORDER BY seq ASC";
            try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                List<AuditEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new AuditEntry(
                            rs.getLong("seq"),
                            LocalDateTime.parse(rs.getString("timestamp")),
                            rs.getString("action"),
                            rs.getString("entity_type"),
                            rs.getString("entity_id"),
                            rs.getString("details"),
                            rs.getString("prev_hash"),
                            rs.getString("hash")
                    ));
                }
                return list;
            } catch (SQLException e) {
                throw new StorageException("Failed to read audit log from database: " + e.getMessage(), e);
            }
        } else if (storage != null && file != null) {
            return parseCsv(storage.readLines(file));
        }
        return Collections.emptyList();
    }

    public synchronized List<AuditEntry> tail(int n) {
        if (db != null) {
            String sql = "SELECT seq, timestamp, action, entity_type, entity_id, details, prev_hash, hash FROM audit_log ORDER BY seq DESC LIMIT ?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, n);
                try (ResultSet rs = ps.executeQuery()) {
                    List<AuditEntry> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new AuditEntry(
                                rs.getLong("seq"),
                                LocalDateTime.parse(rs.getString("timestamp")),
                                rs.getString("action"),
                                rs.getString("entity_type"),
                                rs.getString("entity_id"),
                                rs.getString("details"),
                                rs.getString("prev_hash"),
                                rs.getString("hash")
                        ));
                    }
                    Collections.reverse(list);
                    return list;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to tail audit log from database: " + e.getMessage(), e);
            }
        } else {
            List<AuditEntry> all = readAll();
            return new ArrayList<>(all.subList(Math.max(0, all.size() - n), all.size()));
        }
    }

    public synchronized Verification verify() {
        List<AuditEntry> entries;
        try {
            entries = readAll();
        } catch (StorageException e) {
            return new Verification(false, 0, 0);
        }
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (AuditEntry e : entries) {
            String expectedHash = computeHash(e.prevHash(), e.seq(), e.timestamp(), e.action(), e.entityType(),
                    e.entityId(), e.details());
            if (e.seq() != expectedSeq || !e.prevHash().equals(expectedPrev) || !e.hash().equals(expectedHash)) {
                return new Verification(false, entries.size(), e.seq());
            }
            expectedPrev = e.hash();
            expectedSeq++;
        }
        return new Verification(true, entries.size(), 0);
    }

    private static List<AuditEntry> parseCsv(List<String> lines) {
        List<AuditEntry> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                String[] f = CsvUtil.split(lines.get(i));
                out.add(new AuditEntry(Long.parseLong(f[0]), LocalDateTime.parse(f[1]), f[2], f[3], f[4], f[5], f[6], f[7]));
            } catch (RuntimeException e) {
                throw new StorageException("Corrupt audit row " + (i + 1), e);
            }
        }
        return out;
    }

    private static String computeHash(String prev, long seq, LocalDateTime ts, String action, String type,
                                      String id, String details) {
        String payload = String.join("|", prev, String.valueOf(seq), ts.toString(), action, type, id, details);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
