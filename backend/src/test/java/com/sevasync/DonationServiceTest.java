package com.sevasync;

import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.storage.AuditLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashSet;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonationServiceTest {
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
    void donorInputIsSanitised() throws Exception {
        Donor d = f.sys.donations().registerDonor("  =Annapurna   Mess ", "+91 98765-43210", "", "Block A", DonorType.CANTEEN);
        assertEquals("Annapurna Mess", d.getName());
        assertEquals("9876543210", d.getPhone());
        assertEquals("DON-0001", d.getId());
        assertThrows(InvalidInputException.class,
                () -> f.sys.donations().registerDonor("X", "123", "", "", DonorType.CANTEEN));
        assertThrows(InvalidInputException.class,
                () -> f.sys.donations().registerDonor("X", "9876543210", "not-an-email", "", DonorType.CANTEEN));
    }

    @Test
    void unsafeBatchesAreRejectedAndAudited() throws Exception {
        String donor = f.donor();
        assertThrows(ExpiredBatchException.class,
                () -> f.batch(donor, "Old rice", FoodCategory.COOKED_MEAL, 20, 300, 180));
        assertThrows(UnsafeFoodException.class,
                () -> f.batch(donor, "Warm rice", FoodCategory.COOKED_MEAL, 20, 0, 600));
        assertThrows(EntityNotFoundException.class,
                () -> f.batch("DON-9999", "Rice", FoodCategory.COOKED_MEAL, 20, 0, 120));

        assertTrue(f.sys.donations().listBatches(null).isEmpty());
        long rejected = f.sys.audit().readAll().stream().filter(e -> e.action().equals("BATCH_REJECTED")).count();
        assertEquals(2L, rejected);
    }

    @Test
    void concurrentSubmissionsGetUniqueIds() throws Exception {
        String donor = f.donor();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<FoodBatch>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                Callable<FoodBatch> task = () -> f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 10, 5, 120);
                futures.add(pool.submit(task));
            }
            Set<String> ids = new HashSet<>();
            for (Future<FoodBatch> fut : futures) {
                ids.add(fut.get().getId());
            }
            assertEquals(40, ids.size());
            assertEquals(40, f.sys.donations().listBatches(null).size());
            AuditLogger.Verification v = f.sys.audit().verify();
            assertTrue(v.valid());
        } finally {
            pool.shutdownNow();
        }
    }
}
