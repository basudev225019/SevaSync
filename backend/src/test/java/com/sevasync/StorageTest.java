package com.sevasync;

import com.sevasync.database.DatabaseManager;
import com.sevasync.database.Migrator;
import com.sevasync.exception.ConflictException;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.User;
import com.sevasync.storage.AuditLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
    private final Fixture f = new Fixture();

    @AfterEach
    void tearDown() {
        f.close();
    }

    @Test
    void dataSurvivesARestartAndComesBackAsTheRightSubclass() throws Exception {
        Donor d = f.donor();
        Recipient r = f.recipient("Shelter", NeedLevel.CRITICAL, 75);
        FoodBatch b = f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 40, 0, 200);
        f.restart();
        User back = f.sys.userRepo().findById(r.getId()).orElseThrow();
        Recipient again = assertInstanceOf(Recipient.class, back);
        assertEquals(75, again.getDailyCapacity());
        assertEquals(NeedLevel.CRITICAL, again.getNeedLevel());
        assertEquals(r.getLocation(), again.getLocation());
        assertEquals(b.getExpiresAt(), f.reload(b).getExpiresAt());
    }

    @Test
    void migrationsAreVersionedAndIdempotent() {
        assertEquals(Migrator.SCRIPTS.size(), Migrator.currentVersion(f.sys.db()));
        assertEquals(0, Migrator.migrate(f.sys.db()), "nothing left to apply");
    }

    @Test
    void aVersionOneDatabaseIsKeptAsideNotDestroyed() throws Exception {
        Path dir = Files.createTempDirectory("sevasync-legacy");
        Path file = dir.resolve("sevasync.db");
        DatabaseManager legacy = DatabaseManager.forFile(file);
        legacy.executeScript("CREATE TABLE donors (id TEXT PRIMARY KEY, name TEXT)");
        legacy.update("INSERT INTO donors VALUES ('DON-0001', 'Old Mess')");
        legacy.close();

        DatabaseManager fresh = Migrator.openAndMigrate(file);
        assertEquals(Migrator.SCRIPTS.size(), Migrator.currentVersion(fresh));
        fresh.close();
        try (Stream<Path> s = Files.list(dir)) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("sevasync-v1-legacy-")));
        }
    }

    @Test
    void aFailedTransactionLeavesNoTrace() throws Exception {
        long usersBefore = f.sys.userRepo().count();
        long auditBefore = f.sys.audit().count();
        assertThrows(ConflictException.class, () -> f.sys.db().inTransaction(() -> {
            f.sys.audit().log("TEST", "SOMETHING", "User", "x", "will be rolled back");
            throw new ConflictException("boom");
        }));
        assertEquals(usersBefore, f.sys.userRepo().count());
        assertEquals(auditBefore, f.sys.audit().count());
        assertTrue(f.sys.audit().verify().valid());
    }

    @Test
    void sequentialIdsArePerPrefix() throws Exception {
        Donor d = f.donor();
        FoodBatch a = f.batch(d, "A", FoodCategory.BAKERY, 5, 0, 300);
        FoodBatch b = f.batch(d, "B", FoodCategory.BAKERY, 5, 0, 300);
        assertEquals("BAT-0001", a.getId());
        assertEquals("BAT-0002", b.getId());
        assertTrue(d.getId().startsWith("DON-"));
    }

    @Test
    void auditChainDetectsEditedAndDeletedRows() throws Exception {
        Donor d = f.donor();
        f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 10, 0, 200);
        f.batch(d, "Dal", FoodCategory.COOKED_MEAL, 10, 0, 200);
        AuditLogger.Verification ok = f.sys.audit().verify();
        assertTrue(ok.valid());
        long n = ok.entries();

        f.sys.db().update("UPDATE audit_log SET details = 'nothing happened' WHERE seq = 3");
        AuditLogger.Verification edited = f.sys.audit().verify();
        assertFalse(edited.valid());
        assertEquals(3, edited.firstBadSeq());

        f.restart();
        f.sys.db().update("DELETE FROM audit_log WHERE seq = ?", n);
        f.sys.db().update("DELETE FROM audit_log WHERE seq = 3");
        assertFalse(f.sys.audit().verify().valid());
    }

    @Test
    void anAttackerWithoutTheKeyCannotForgeAValidChain() throws Exception {
        Donor d = f.donor();
        f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 10, 0, 200);
        // recompute a plain SHA-256 "hash" for an edited row, as one could against an unkeyed chain
        List<AuditLogger.AuditEntry> rows = f.sys.audit().readAll();
        AuditLogger.AuditEntry last = rows.get(rows.size() - 1);
        String forged = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(("forged" + last.prevHash()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        f.sys.db().update("UPDATE audit_log SET details = 'forged', hash = ? WHERE seq = ?", forged, last.seq());
        assertFalse(f.sys.audit().verify().valid());
    }

    @Test
    void backupsAndCsvExportsAreWrittenToTheDataFolder() throws Exception {
        Donor d = f.donor();
        f.batch(d, "Rice, with \"ghee\"", FoodCategory.COOKED_MEAL, 10, 0, 200);
        Path snap = f.sys.backups().snapshot();
        assertTrue(Files.size(snap) > 0);
        assertEquals(1, f.sys.backups().listSnapshots().size());

        Path folder = f.sys.backups().exportCsv();
        List<String> batches = Files.readAllLines(folder.resolve("food_batches.csv"));
        assertEquals(2, batches.size());
        assertTrue(batches.get(1).contains("\"Rice, with \"\"ghee\"\"\""));
        assertTrue(Files.readAllLines(folder.resolve("users.csv")).get(0).contains("location.lat"));
        assertTrue(Files.exists(folder.resolve("audit_log.csv")));
    }
}
