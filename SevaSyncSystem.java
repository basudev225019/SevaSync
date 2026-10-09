package com.sevasync;

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
import com.sevasync.validation.SafetyValidator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;

/** Composition root: builds the repositories and services and wires them together. */
public class SevaSyncSystem implements AutoCloseable {
    private final Clock clock;
    private final FileStorageManager storage;
    private final AuditLogger audit;
    private final CsvRepository<Donor> donorRepo;
    private final CsvRepository<Recipient> recipientRepo;
    private final CsvRepository<FoodBatch> batchRepo;
    private final CsvRepository<Dispatch> dispatchRepo;
    private final DonationService donationService;
    private final RecipientService recipientService;
    private final DispatchService dispatchService;
    private final MatchingService matchingService;
    private final ImpactReportService reportService;
    private final BackupService backupService;
    private final SafetyValidator validator;
    private ExpiryMonitor monitor;

    public SevaSyncSystem(Path dataDir, Clock clock) {
        this.clock = clock;
        ReentrantLock lock = new ReentrantLock(true);
        storage = new FileStorageManager(dataDir);
        audit = new AuditLogger(storage, "audit_log.csv", clock);
        donorRepo = new CsvRepository<>(storage, "donors.csv", Donor.CSV_HEADER, Donor::fromCsv, "DON");
        recipientRepo = new CsvRepository<>(storage, "recipients.csv", Recipient.CSV_HEADER, Recipient::fromCsv, "REC");
        batchRepo = new CsvRepository<>(storage, "batches.csv", FoodBatch.CSV_HEADER, FoodBatch::fromCsv, "BAT");
        dispatchRepo = new CsvRepository<>(storage, "dispatches.csv", Dispatch.CSV_HEADER, Dispatch::fromCsv, "DSP");
        validator = new SafetyValidator(clock);
        donationService = new DonationService(donorRepo, batchRepo, validator, audit, clock, lock);
        recipientService = new RecipientService(recipientRepo, dispatchRepo, audit, clock, lock);
        dispatchService = new DispatchService(batchRepo, dispatchRepo, recipientService, validator, audit, clock, lock);
        matchingService = new MatchingService(batchRepo, recipientRepo, dispatchRepo, recipientService,
                dispatchService, audit, clock, lock);
        reportService = new ImpactReportService(donorRepo, recipientRepo, batchRepo, dispatchRepo, storage, clock);
        backupService = new BackupService(donorRepo, recipientRepo, batchRepo, dispatchRepo, audit, clock, lock);
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
    }

    public Clock clock() { return clock; }
    public FileStorageManager storage() { return storage; }
    public AuditLogger audit() { return audit; }
    public SafetyValidator validator() { return validator; }
    public DonationService donations() { return donationService; }
    public RecipientService recipients() { return recipientService; }
    public DispatchService dispatches() { return dispatchService; }
    public MatchingService matching() { return matchingService; }
    public ImpactReportService reports() { return reportService; }
    public BackupService backups() { return backupService; }
}
