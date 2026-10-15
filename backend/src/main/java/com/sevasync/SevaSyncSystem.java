package com.sevasync;

import com.sevasync.database.DatabaseManager;
import com.sevasync.database.Migrator;
import com.sevasync.event.EventBus;
import com.sevasync.matching.StrategyRegistry;
import com.sevasync.security.AuthService;
import com.sevasync.security.LoginThrottle;
import com.sevasync.security.PasswordHasher;
import com.sevasync.security.SecretStore;
import com.sevasync.service.DispatchService;
import com.sevasync.service.DonationService;
import com.sevasync.service.ExpiryMonitor;
import com.sevasync.service.ImpactReportService;
import com.sevasync.service.MatchingService;
import com.sevasync.service.NetworkService;
import com.sevasync.service.RecipientService;
import com.sevasync.service.UserService;
import com.sevasync.service.ViewAssembler;
import com.sevasync.simulation.BenchmarkService;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.BackupService;
import com.sevasync.storage.CredentialRepository;
import com.sevasync.storage.FileStorageManager;
import com.sevasync.storage.SessionRepository;
import com.sevasync.storage.SettingsRepository;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteDonorRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteRecipientRepository;
import com.sevasync.storage.SqliteUserRepository;
import com.sevasync.storage.SqliteVolunteerRepository;
import com.sevasync.util.Json;
import com.sevasync.validation.SafetyValidator;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/**
 * Composition root: builds every repository and service once and wires them together (manual dependency
 * injection - no framework). Everything else in the code base receives its collaborators through constructors.
 */
public class SevaSyncSystem implements AutoCloseable {
    public static final String VERSION = "2.0.0";

    /**
     * @param passwordIterations PBKDF2 work factor (lower it only in tests)
     * @param secretBase64       server secret from the environment, or {@code null} to use {@code data/secret.key}
     */
    public record Options(Path dataDir, Clock clock, int passwordIterations, String secretBase64) {
        public static Options defaults(Path dataDir) {
            return new Options(dataDir, Clock.systemDefaultZone(), PasswordHasher.DEFAULT_ITERATIONS,
                    System.getenv("SEVASYNC_SECRET"));
        }
    }

    private final Clock clock;
    private final DatabaseManager db;
    private final FileStorageManager storage;
    private final AuditLogger audit;
    private final EventBus events;
    private final SqliteUserRepository userRepo;
    private final SqliteFoodBatchRepository batchRepo;
    private final SqliteDispatchRepository dispatchRepo;
    private final AuthService auth;
    private final UserService users;
    private final DonationService donations;
    private final DispatchService dispatches;
    private final MatchingService matching;
    private final ImpactReportService reports;
    private final NetworkService network;
    private final BackupService backups;
    private final BenchmarkService benchmark;
    private final ViewAssembler views;
    private final StrategyRegistry strategies;
    private final ExpiryMonitor monitor;

    public SevaSyncSystem(Path dataDir, Clock clock) {
        this(new Options(dataDir, clock, PasswordHasher.DEFAULT_ITERATIONS, System.getenv("SEVASYNC_SECRET")));
    }

    public SevaSyncSystem(Options o) {
        this.clock = o.clock();
        Json.setZone(clock.getZone());
        this.storage = new FileStorageManager(o.dataDir());
        this.db = Migrator.openAndMigrate(storage.resolve("sevasync.db"));
        byte[] secret = SecretStore.load(storage.getDataDir(), o.secretBase64());

        // storage
        this.audit = new AuditLogger(db, secret, clock);
        this.events = new EventBus(db, clock);
        this.userRepo = new SqliteUserRepository(db);
        this.batchRepo = new SqliteFoodBatchRepository(db);
        this.dispatchRepo = new SqliteDispatchRepository(db);
        SqliteDonorRepository donorRepo = new SqliteDonorRepository(userRepo);
        SqliteRecipientRepository recipientRepo = new SqliteRecipientRepository(userRepo);
        SqliteVolunteerRepository volunteerRepo = new SqliteVolunteerRepository(userRepo);
        SettingsRepository settings = new SettingsRepository(db);

        // security
        this.auth = new AuthService(db, userRepo, new CredentialRepository(db), new SessionRepository(db),
                new PasswordHasher(o.passwordIterations()), new LoginThrottle(5, Duration.ofMinutes(10), clock),
                audit, events, clock);

        // domain services; matching is created last, so the others reach it through a late-bound callback
        SafetyValidator validator = new SafetyValidator(clock);
        RecipientService capacity = new RecipientService(dispatchRepo, clock);
        this.views = new ViewAssembler(userRepo, batchRepo, clock);
        this.strategies = new StrategyRegistry();
        Runnable rematch = this::autoMatch;
        this.donations = new DonationService(db, batchRepo, dispatchRepo, validator, audit, events, clock, rematch);
        this.dispatches = new DispatchService(db, batchRepo, dispatchRepo, recipientRepo, donorRepo, capacity,
                validator, audit, events, clock, rematch);
        this.matching = new MatchingService(db, batchRepo, dispatchRepo, recipientRepo, donorRepo, capacity,
                dispatches, strategies, settings, views, audit, events, clock);
        this.users = new UserService(db, userRepo, auth, audit, events);
        this.reports = new ImpactReportService(userRepo, batchRepo, dispatchRepo, clock);
        this.network = new NetworkService(donorRepo, recipientRepo, volunteerRepo, batchRepo, dispatchRepo, capacity,
                clock);
        this.backups = new BackupService(db, storage, userRepo, batchRepo, dispatchRepo, audit, clock);
        this.benchmark = new BenchmarkService(strategies);
        this.monitor = new ExpiryMonitor(dispatches, auth, 60_000);
    }

    private void autoMatch() {
        if (matching != null) {
            matching.autoMatch();
        }
    }

    public void startBackgroundJobs() {
        monitor.start();
    }

    @Override
    public void close() {
        monitor.close();
        db.close();
    }

    public Clock clock() { return clock; }
    public DatabaseManager db() { return db; }
    public FileStorageManager storage() { return storage; }
    public AuditLogger audit() { return audit; }
    public EventBus events() { return events; }
    public AuthService auth() { return auth; }
    public UserService users() { return users; }
    public DonationService donations() { return donations; }
    public DispatchService dispatches() { return dispatches; }
    public MatchingService matching() { return matching; }
    public ImpactReportService reports() { return reports; }
    public NetworkService network() { return network; }
    public BackupService backups() { return backups; }
    public BenchmarkService benchmark() { return benchmark; }
    public ViewAssembler views() { return views; }
    public StrategyRegistry strategies() { return strategies; }
    public ExpiryMonitor monitor() { return monitor; }
    public SqliteUserRepository userRepo() { return userRepo; }
    public SqliteFoodBatchRepository batchRepo() { return batchRepo; }
    public SqliteDispatchRepository dispatchRepo() { return dispatchRepo; }
}
