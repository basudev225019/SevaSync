package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.util.TimeUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only, tamper-evident audit trail.
 *
 * <p>Each row stores {@code HMAC-SHA256(serverKey, previousHash | row fields)}. Because the key never leaves the
 * server, someone with write access to the database file can delete or edit rows but cannot recompute a valid chain,
 * so {@link #verify()} pinpoints the first altered row. (A plain SHA-256 chain could simply be recomputed.)
 *
 * <p>{@link #log} must be called inside the business transaction it describes: the audit row and the change
 * commit or roll back together.
 */
public class AuditLogger {
    private static final String GENESIS = "0".repeat(64);
    private static final String COLUMNS = "seq, timestamp, actor_id, action, entity_type, entity_id, details, prev_hash, hash";

    public record AuditEntry(long seq, LocalDateTime timestamp, String actorId, String action, String entityType,
                             String entityId, String details, String prevHash, String hash) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", seq);
            m.put("timestamp", timestamp);
            m.put("actorId", actorId);
            m.put("action", action);
            m.put("entityType", entityType);
            m.put("entityId", entityId);
            m.put("details", details);
            m.put("prevHash", prevHash);
            m.put("hash", hash);
            return m;
        }
    }

    public record Verification(boolean valid, long entries, long firstBadSeq, String reason) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("valid", valid);
            m.put("entries", entries);
            m.put("firstBadSeq", firstBadSeq == 0 ? null : firstBadSeq);
            m.put("reason", reason);
            return m;
        }
    }

    /** Actor id used for actions the system takes on its own (expiry monitor, auto-matching). */
    public static final String SYSTEM = "SYSTEM";

    private final DatabaseManager db;
    private final SecretKeySpec key;
    private final Clock clock;

    public AuditLogger(DatabaseManager db, byte[] secret, Clock clock) {
        this.db = db;
        this.key = new SecretKeySpec(secret.clone(), "HmacSHA256");
        this.clock = clock;
    }

    public AuditEntry log(String actorId, String action, String entityType, String entityId, String details) {
        return db.inTransaction(() -> {
            String[] last = db.queryOne("SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}).orElse(new String[]{"0", GENESIS});
            long seq = Long.parseLong(last[0]) + 1;
            String prev = last[1];
            LocalDateTime ts = TimeUtil.now(clock);
            String actor = actorId == null ? SYSTEM : actorId;
            String clean = details == null ? "" : details.replace('\r', ' ').replace('\n', ' ');
            String hash = mac(prev, seq, ts, actor, action, entityType, entityId, clean);
            db.update("INSERT INTO audit_log (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    seq, ts, actor, action, entityType, entityId, clean, prev, hash);
            return new AuditEntry(seq, ts, actor, action, entityType, entityId, clean, prev, hash);
        });
    }

    /** Newest {@code limit} entries, optionally filtered by entity, oldest first. */
    public List<AuditEntry> tail(int limit, String entityType, String entityId) {
        List<AuditEntry> rows;
        if (entityType != null && entityId != null) {
            rows = db.query("SELECT " + COLUMNS + " FROM audit_log WHERE entity_type = ? AND entity_id = ? "
                    + "ORDER BY seq DESC LIMIT ?", AuditLogger::map, entityType, entityId, limit);
        } else {
            rows = db.query("SELECT " + COLUMNS + " FROM audit_log ORDER BY seq DESC LIMIT ?", AuditLogger::map,
                    limit);
        }
        List<AuditEntry> out = new ArrayList<>(rows);
        java.util.Collections.reverse(out);
        return out;
    }

    public List<AuditEntry> readAll() {
        return db.query("SELECT " + COLUMNS + " FROM audit_log ORDER BY seq", AuditLogger::map);
    }

    public long count() {
        return db.queryLong("SELECT COUNT(*) FROM audit_log");
    }

    /** Recomputes the whole chain and reports the first row whose position, link or MAC does not check out. */
    public Verification verify() {
        List<AuditEntry> entries = readAll();
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (AuditEntry e : entries) {
            if (e.seq() != expectedSeq) {
                return new Verification(false, entries.size(), e.seq(), "row " + expectedSeq + " is missing");
            }
            if (!e.prevHash().equals(expectedPrev)) {
                return new Verification(false, entries.size(), e.seq(), "link to previous row is broken");
            }
            String expected = mac(e.prevHash(), e.seq(), e.timestamp(), e.actorId(), e.action(), e.entityType(),
                    e.entityId(), e.details());
            if (!java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    e.hash().getBytes(StandardCharsets.US_ASCII))) {
                return new Verification(false, entries.size(), e.seq(), "row content was altered");
            }
            expectedPrev = e.hash();
            expectedSeq++;
        }
        return new Verification(true, entries.size(), 0, "chain intact");
    }

    private static AuditEntry map(ResultSet rs) throws SQLException {
        return new AuditEntry(rs.getLong("seq"), LocalDateTime.parse(rs.getString("timestamp")),
                rs.getString("actor_id"), rs.getString("action"), rs.getString("entity_type"),
                rs.getString("entity_id"), rs.getString("details"), rs.getString("prev_hash"), rs.getString("hash"));
    }

    private String mac(String prev, long seq, LocalDateTime ts, String actor, String action, String type, String id,
                       String details) {
        String payload = String.join("|", prev, String.valueOf(seq), ts.format(DatabaseManager.TIMESTAMP), actor,
                action, type, id, details);
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(key);
            return HexFormat.of().formatHex(m.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
