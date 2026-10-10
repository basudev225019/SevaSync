package com.sevasync;

import com.sevasync.database.DatabaseManager;
import com.sevasync.model.Dispatch;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Recipient;
import com.sevasync.service.DispatchService;
import com.sevasync.service.DonationService;
import com.sevasync.service.ExpiryMonitor;
import com.sevasync.service.ImpactReportService;
import com.sevasync.service.MatchingService;
import com.sevasync.service.RecipientService;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.BackupService;
import com.sevasync.storage.CsvRepository;
import com.sevasync.storage.FileStorageManager;
import com.sevasync.storage.Repository;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteDonorRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteRecipientRepository;
import com.sevasync.validation.SafetyValidator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Composition root: builds SQLite repositories and services and wires them together.
 */
public class SevaSyncSystem implements AutoCloseable {
    private final Clock clock;
    private final DatabaseManager db;
    private final FileStorageManager storage;
    private final AuditLogger audit;
    private final Repository<Donor> donorRepo;
    private final Repository<Recipient> recipientRepo;
    private final Repository<FoodBatch> batchRepo;
    private final Repository<Dispatch> dispatchRepo;
    private final DonationService donationService;
    private final RecipientService recipientService;
    private final DispatchService dispatchService;
    private final MatchingService matchingService;
    private final ImpactReportService reportService;
    private final BackupService backupService;
    private final SafetyValidator validator;
    private ExpiryMonitor monitor;

    public SevaSyncSystem(Path dataDir, Clock clock) {
        this(dataDir, clock, true);
    }

    public SevaSyncSystem(Path dataDir, Clock clock, boolean useSqlite) {
        this.clock = clock;
        ReentrantLock lock = new ReentrantLock(true);
        this.storage = new FileStorageManager(dataDir);

        if (useSqlite) {
            Path dbPath = dataDir.resolve("sevasync.db");
            this.db = DatabaseManager.forFile(dbPath);
            this.audit = new AuditLogger(db, clock);
            this.donorRepo = new SqliteDonorRepository(db);
            this.recipientRepo = new SqliteRecipientRepository(db);
            this.batchRepo = new SqliteFoodBatchRepository(db);
            this.dispatchRepo = new SqliteDispatchRepository(db);
        } else {
            this.db = null;
            this.audit = new AuditLogger(storage, "audit_log.csv", clock);
            this.donorRepo = new CsvRepository<>(storage, "donors.csv", Donor.CSV_HEADER, Donor::fromCsv, "DON");
            this.recipientRepo = new CsvRepository<>(storage, "recipients.csv", Recipient.CSV_HEADER, Recipient::fromCsv, "REC");
            this.batchRepo = new CsvRepository<>(storage, "batches.csv", FoodBatch.CSV_HEADER, FoodBatch::fromCsv, "BAT");
            this.dispatchRepo = new CsvRepository<>(storage, "dispatches.csv", Dispatch.CSV_HEADER, Dispatch::fromCsv, "DSP");
        }

        this.validator = new SafetyValidator(clock);
        this.donationService = new DonationService(donorRepo, batchRepo, validator, audit, clock, lock);
        this.recipientService = new RecipientService(recipientRepo, dispatchRepo, audit, clock, lock);
        this.dispatchService = new DispatchService(batchRepo, dispatchRepo, recipientService, validator, audit, clock, lock);
        this.matchingService = new MatchingService(batchRepo, recipientRepo, dispatchRepo, recipientService,
                dispatchService, audit, clock, lock);
        this.reportService = new ImpactReportService(donorRepo, recipientRepo, batchRepo, dispatchRepo, storage, clock);
        this.backupService = new BackupService(donorRepo, recipientRepo, batchRepo, dispatchRepo, audit, clock, lock);
    }

    public synchronized void startExpiryMonitor(long intervalMillis) {
        if (monitor == null) {
            monitor = new ExpiryMonitor(dispatchService, intervalMillis);
        }
        monitor.start();
    }

    @Override
    public synchronized void close() {
        if (monitor != null) {
            monitor.stop();
        }
        if (db != null) {
            db.close();
        }
    }

    public Clock clock() { return clock; }
    public DatabaseManager db() { return db; }
    public FileStorageManager storage() { return storage; }
    public AuditLogger audit() { return audit; }
    public SafetyValidator validator() { return validator; }
    public DonationService donations() { return donationService; }
    public RecipientService recipients() { return recipientService; }
    public DispatchService dispatches() { return dispatchService; }
    public MatchingService matching() { return matchingService; }
    public ImpactReportService reports() { return reportService; }
    public BackupService backups() { return backupService; }
    public Repository<Donor> donors() { return donorRepo; }
    public Repository<Recipient> recipientRepo() { return recipientRepo; }
    public Repository<FoodBatch> batches() { return batchRepo; }
    public Repository<Dispatch> dispatchRepo() { return dispatchRepo; }
}
