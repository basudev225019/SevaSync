package com.sevasync;

import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.service.MatchingService.MatchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingServiceTest {
    private Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture();
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private static String describe(List<Dispatch> list) {
        StringBuilder sb = new StringBuilder();
        for (Dispatch d : list) {
            sb.append(d.getBatchId()).append("->").append(d.getRecipientId()).append(':').append(d.getServings()).append(' ');
        }
        return sb.toString().trim();
    }

    /** Earliest-expiring batch goes to the neediest recipient; batches are split when capacity runs out. */
    @Test
    void ordersByExpiryAndNeedAndSplitsBatches() throws Exception {
        String donor = f.donor();
        Recipient r1 = f.recipient("Critical Home", NeedLevel.CRITICAL, 50);
        Recipient r2 = f.recipient("High Shelter", NeedLevel.HIGH, 100);
        f.recipient("Normal Kitchen", NeedLevel.NORMAL, 100);
        FoodBatch a = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 80, 60, 180);      // expires 14:00
        FoodBatch b = f.batch(donor, "Dal", FoodCategory.COOKED_MEAL, 40, 30, 120);       // expires 13:30 (earliest)
        FoodBatch c = f.batch(donor, "Buns", FoodCategory.BAKERY, 30, 60, 600);           // expires 21:00

        MatchResult result = f.sys.matching().runMatching();

        String expected = b.getId() + "->" + r1.getId() + ":40 "
                + a.getId() + "->" + r1.getId() + ":10 "
                + a.getId() + "->" + r2.getId() + ":70 "
                + c.getId() + "->" + r2.getId() + ":30";
        assertEquals(expected, describe(result.dispatches()));
        assertEquals(0, result.unallocatedServings());
        assertEquals(150, result.allocatedServings());
        for (FoodBatch batch : f.sys.donations().listBatches(null)) {
            assertEquals(BatchStatus.ALLOCATED, batch.getStatus());
            assertEquals(0, batch.getRemainingServings());
        }
    }

    @Test
    void previewDoesNotPersistAnything() throws Exception {
        String donor = f.donor();
        f.recipient("Home", NeedLevel.HIGH, 100);
        f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 10, 180);

        MatchResult preview = f.sys.matching().preview();

        assertEquals(1, preview.dispatches().size());
        assertEquals(false, preview.committed());
        assertTrue(f.sys.dispatches().list(null).isEmpty());
        assertEquals(40, f.sys.donations().listBatches(BatchStatus.AVAILABLE).get(0).getRemainingServings());
    }

    @Test
    void respectsRecipientDailyCapacityAndIsRepeatable() throws Exception {
        String donor = f.donor();
        f.recipient("Small Home", NeedLevel.CRITICAL, 20);
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 50, 10, 180);

        MatchResult first = f.sys.matching().runMatching();
        assertEquals(20, first.allocatedServings());
        assertEquals(30, first.unallocatedServings());
        FoodBatch stored = f.sys.donations().getBatch(b.getId());
        assertEquals(BatchStatus.AVAILABLE, stored.getStatus());
        assertEquals(30, stored.getRemainingServings());

        MatchResult second = f.sys.matching().runMatching();   // capacity for today is used up
        assertTrue(second.dispatches().isEmpty());
        assertEquals(30, second.unallocatedServings());

        f.clock.advance(Duration.ofHours(24));                  // new day, but the batch has expired by then
        assertTrue(f.sys.matching().runMatching().dispatches().isEmpty());
        assertEquals(BatchStatus.EXPIRED, f.sys.donations().getBatch(b.getId()).getStatus());
    }

    @Test
    void doesNothingWithoutRecipientsOrBatches() {
        MatchResult r = f.sys.matching().runMatching();
        assertTrue(r.dispatches().isEmpty());
        assertEquals(0, r.unallocatedServings());
    }

    @Test
    void skipsBatchesTooCloseToExpiry() throws Exception {
        String donor = f.donor();
        f.recipient("Home", NeedLevel.HIGH, 100);
        f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 0, 40);   // 40 min left
        f.clock.advance(Duration.ofMinutes(35));                        // 5 min left: not worth dispatching
        assertTrue(f.sys.matching().preview().dispatches().isEmpty());
    }
}
