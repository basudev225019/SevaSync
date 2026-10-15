package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.User;
import com.sevasync.util.CsvUtil;
import com.sevasync.util.TimeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Backups and exports.
 * <ul>
 *   <li>{@link #snapshot()} - a consistent copy of the live database ({@code VACUUM INTO}), safe while running.</li>
 *   <li>{@link #exportCsv()} - users, batches, dispatches and the audit trail as spreadsheet-friendly CSV files.</li>
 * </ul>
 */
public class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int KEEP_SNAPSHOTS = 10;

    private final DatabaseManager db;
    private final FileStorageManager files;
    private final SqliteUserRepository users;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final AuditLogger audit;
    private final Clock clock;

    public BackupService(DatabaseManager db, FileStorageManager files, SqliteUserRepository users,
                         SqliteFoodBatchRepository batches, SqliteDispatchRepository dispatches, AuditLogger audit,
                         Clock clock) {
        this.db = db;
        this.files = files;
        this.users = users;
        this.batches = batches;
        this.dispatches = dispatches;
        this.audit = audit;
        this.clock = clock;
    }

    /** Writes {@code backups/sevasync-<timestamp>.db} and prunes old snapshots. */
    public Path snapshot() {
        Path dir = files.resolve("backups");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new StorageException("Cannot create backup directory", e);
        }
        Path target = dir.resolve("sevasync-" + TimeUtil.now(clock).format(STAMP) + ".db");
        db.snapshotTo(target);
        prune(dir);
        return target;
    }

    public List<Map<String, Object>> listSnapshots() {
        Path dir = files.resolve("backups");
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".db"))
                    .sorted(Comparator.reverseOrder()).toList()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("file", p.getFileName().toString());
                m.put("sizeBytes", Files.size(p));
                out.add(m);
            }
        } catch (IOException e) {
            throw new StorageException("Cannot list backups", e);
        }
        return out;
    }

    /** Writes one CSV per table into {@code exports/<timestamp>/} and returns that folder. */
    public Path exportCsv() {
        Path dir = files.resolve("exports").resolve(TimeUtil.now(clock).format(STAMP));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new StorageException("Cannot create export directory", e);
        }
        new CsvRepository<User>(files, dir.resolve("users.csv")).writeAll(users.findAll());
        new CsvRepository<FoodBatch>(files, dir.resolve("food_batches.csv")).writeAll(batches.findAll());
        new CsvRepository<Dispatch>(files, dir.resolve("dispatches.csv")).writeAll(dispatches.findAll());
        List<String> lines = new ArrayList<>();
        lines.add("seq,timestamp,actor,action,entity_type,entity_id,details,hash");
        for (AuditLogger.AuditEntry e : audit.readAll()) {
            lines.add(CsvUtil.join(new String[]{String.valueOf(e.seq()), e.timestamp().toString(), e.actorId(),
                    e.action(), e.entityType(), e.entityId(), e.details(), e.hash()}));
        }
        files.writeLinesAtomically(dir.resolve("audit_log.csv"), lines);
        return dir;
    }

    private static void prune(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> snaps = s.filter(f -> f.getFileName().toString().endsWith(".db"))
                    .sorted(Comparator.reverseOrder()).toList();
            for (int i = KEEP_SNAPSHOTS; i < snaps.size(); i++) {
                Files.deleteIfExists(snaps.get(i));
            }
        } catch (IOException ignored) {
            // pruning is best effort
        }
    }
}
