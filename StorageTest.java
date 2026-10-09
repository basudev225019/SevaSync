package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.exception.StorageException;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
    private Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture();
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    @Test
    void dataSurvivesRestartIncludingSpecialCharacters() throws Exception {
        String donorId = f.sys.donations()
                .registerDonor("Annapurna, \"Main\" Mess", "9876543210", "a@b.in", "Block 1, North", DonorType.CANTEEN).getId();
        f.recipient("Home", NeedLevel.HIGH, 50);
        FoodBatch b = f.batch(donorId, "Rice, dal & \"pickle\"", FoodCategory.COOKED_MEAL, 25, 10, 120);

        f.restart();

        assertEquals("Annapurna, \"Main\" Mess", f.sys.donations().getDonor(donorId).getName());
        FoodBatch back = f.sys.donations().getBatch(b.getId());
        assertEquals("Rice, dal & \"pickle\"", back.getFoodName());
        assertEquals(b.getExpiresAt(), back.getExpiresAt());
        assertEquals("BAT-0002", f.sys.donations().submitBatch(donorId, "More", FoodCategory.BAKERY, true, 5,
                f.now(), 120).getId());
    }

    @Test
    void corruptDataFileIsReportedWithLineNumber() throws Exception {
        f.donor();
        f.sys.close();
        Path file = f.dir.resolve("donors.csv");
        Files.writeString(file, Files.readString(file) + "DON-0002,Broken\n");
        StorageException e = assertThrows(StorageException.class, () -> new SevaSyncSystem(f.dir, f.clock));
        assertTrue(e.getMessage().contains("donors.csv"));
        assertTrue(e.getMessage().contains("row 3"));
    }

    @Test
    void auditTrailIsHashChainedAndDetectsTampering() throws Exception {
        String donor = f.donor();
        f.recipient("Home", NeedLevel.HIGH, 50);
        f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 25, 10, 120);
        assertTrue(f.sys.audit().verify().valid());
        int before = f.sys.audit().readAll().size();

        f.restart();                                   // sequence and hash chain continue after restart
        f.recipient("Home 2", NeedLevel.NORMAL, 10);
        assertEquals(before + 1, f.sys.audit().readAll().size());
        assertTrue(f.sys.audit().verify().valid());

        f.sys.close();
        Path file = f.dir.resolve("audit_log.csv");
        Files.writeString(file, Files.readString(file).replace("25 servings", "9999 servings"));
        AuditLogger tampered = new AuditLogger(new com.sevasync.storage.FileStorageManager(f.dir), "audit_log.csv", f.clock);
        AuditLogger.Verification v = tampered.verify();
        assertFalse(v.valid());
        assertEquals(3L, v.firstBadSeq());
    }

    @Test
    void backupAndRestoreRoundTrip() throws Exception {
        String donor = f.donor();
        f.recipient("Home", NeedLevel.HIGH, 50);
        f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 25, 10, 120);
        f.sys.matching().runMatching();
        Path backup = f.dir.resolve("snap.ser");
        BackupService.Snapshot snap = f.sys.backups().backup(backup);
        assertEquals(1, snap.donors().size());
        assertEquals(1, snap.dispatches().size());

        f.sys.donations().registerDonor("Late Donor", "9876543211", "", "", DonorType.EVENT_COORDINATOR);
        assertEquals(2, f.sys.donations().listDonors().size());

        f.sys.backups().restore(backup);

        assertEquals(1, f.sys.donations().listDonors().size());
        assertEquals(1, f.sys.dispatches().list(null).size());
    }

    @Test
    void restoreRefusesForeignOrCorruptFiles() throws Exception {
        Path foreign = f.dir.resolve("foreign.ser");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(foreign))) {
            out.writeObject(new HashMap<>(java.util.Map.of("a", List.of(1))));
        }
        assertThrows(SevaSyncException.class, () -> f.sys.backups().restore(foreign));
        Path junk = f.dir.resolve("junk.ser");
        Files.write(junk, new byte[]{1, 2, 3, 4});
        assertThrows(SevaSyncException.class, () -> f.sys.backups().restore(junk));
    }

    @Test
    void expiryMonitorThreadExpiresBatchesInTheBackground() throws Exception {
        String donor = f.donor();
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 25, 10, 60);
        f.sys.startExpiryMonitor(20);
        f.clock.advance(Duration.ofMinutes(120));

        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline
                && f.sys.donations().getBatch(b.getId()).getStatus() != com.sevasync.model.BatchStatus.EXPIRED) {
            Thread.sleep(20);
        }
        assertEquals(com.sevasync.model.BatchStatus.EXPIRED, f.sys.donations().getBatch(b.getId()).getStatus());
    }
}
