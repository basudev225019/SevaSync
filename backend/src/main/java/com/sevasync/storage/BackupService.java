package com.sevasync.storage;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Recipient;
import com.sevasync.util.TimeUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/** Binary snapshots using Java Object Streams / Serialization. Restore only accepts SevaSync classes. */
public class BackupService {

    public record Snapshot(LocalDateTime takenAt, ArrayList<Donor> donors, ArrayList<Recipient> recipients,
                           ArrayList<FoodBatch> batches, ArrayList<Dispatch> dispatches) implements Serializable {
        public String summary() {
            return donors.size() + " donors, " + recipients.size() + " recipients, "
                    + batches.size() + " batches, " + dispatches.size() + " dispatches (taken " + takenAt + ")";
        }
    }

    private static final String ALLOWED = "com.sevasync.**;java.util.ArrayList;java.time.*;java.lang.Enum;"
            + "java.lang.Record;java.lang.Object;java.lang.String;java.lang.Number;java.lang.Integer;"
            + "java.lang.Boolean;!*";

    private final Repository<Donor> donors;
    private final Repository<Recipient> recipients;
    private final Repository<FoodBatch> batches;
    private final Repository<Dispatch> dispatches;
    private final AuditLogger audit;
    private final Clock clock;
    private final ReentrantLock lock;

    public BackupService(Repository<Donor> donors, Repository<Recipient> recipients,
                         Repository<FoodBatch> batches, Repository<Dispatch> dispatches,
                         AuditLogger audit, Clock clock, ReentrantLock lock) {
        this.donors = donors;
        this.recipients = recipients;
        this.batches = batches;
        this.dispatches = dispatches;
        this.audit = audit;
        this.clock = clock;
        this.lock = lock;
    }

    public Snapshot backup(Path target) throws SevaSyncException {
        lock.lock();
        try {
            Snapshot snap = new Snapshot(TimeUtil.now(clock), new ArrayList<>(donors.findAll()),
                    new ArrayList<>(recipients.findAll()), new ArrayList<>(batches.findAll()),
                    new ArrayList<>(dispatches.findAll()));
            Path abs = target.toAbsolutePath();
            if (abs.getParent() != null) {
                Files.createDirectories(abs.getParent());
            }
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(abs)))) {
                out.writeObject(snap);
            }
            audit.log("BACKUP_CREATED", "System", abs.getFileName().toString(), snap.summary());
            return snap;
        } catch (IOException e) {
            throw new SevaSyncException("Backup failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /** Read a snapshot without applying it. */
    public Snapshot inspect(Path source) throws SevaSyncException {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(ALLOWED));
            Object o = in.readObject();
            if (o instanceof Snapshot s) {
                return s;
            }
            throw new SevaSyncException("Not a SevaSync backup file");
        } catch (InvalidClassException e) {
            throw new SevaSyncException("Not a valid SevaSync backup: " + e.getMessage(), e);
        } catch (IOException | ClassNotFoundException e) {
            throw new SevaSyncException("Cannot read backup: " + e.getMessage(), e);
        }
    }

    public Snapshot restore(Path source) throws SevaSyncException {
        Snapshot snap = inspect(source);
        lock.lock();
        try {
            donors.replaceAll(snap.donors());
            recipients.replaceAll(snap.recipients());
            batches.replaceAll(snap.batches());
            dispatches.replaceAll(snap.dispatches());
            audit.log("BACKUP_RESTORED", "System", source.getFileName().toString(), snap.summary());
            return snap;
        } finally {
            lock.unlock();
        }
    }
}
