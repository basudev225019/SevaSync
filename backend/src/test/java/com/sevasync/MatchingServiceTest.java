package com.sevasync;

import com.sevasync.model.AccountStatus;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.service.MatchingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matching as the platform runs it: real repositories, capacity bookkeeping and persistence. */
class MatchingServiceTest {
    private final Fixture f = new Fixture();

    @AfterEach
    void tearDown() {
        f.close();
    }

    @Test
    void previewChangesNothingButRunPersists() throws Exception {
        Donor d = f.donor();
        f.recipient("Shelter", NeedLevel.HIGH, 100);
        FoodBatch b = f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 40, 0, 200);

        MatchingService.MatchResult preview = f.sys.matching().preview(f.admin, null);
        assertEquals(40, preview.allocatedServings());
        assertEquals(0, f.sys.dispatchRepo().count());
        assertEquals(40, f.reload(b).getRemainingServings());

        List<Dispatch> created = f.match();
        assertEquals(1, created.size());
        assertEquals(BatchStatus.ALLOCATED, f.reload(b).getStatus());
        assertEquals("smart", created.get(0).getStrategy());
        assertTrue(created.get(0).getDistanceKm() > 0);
    }

    @Test
    void dailyCapacityHoldsAcrossRunsAndResetsTomorrow() throws Exception {
        Donor d = f.donor();
        f.recipient("Small Home", NeedLevel.CRITICAL, 30);
        f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 20, 0, 200);
        f.match();
        f.batch(d, "Dal", FoodCategory.COOKED_MEAL, 20, 0, 200);
        List<Dispatch> second = f.match();
        assertEquals(10, second.stream().mapToInt(Dispatch::getServings).sum(), "only 10 servings of room left");

        f.clock.advance(Duration.ofHours(14)); // past midnight, still within no batch's window -> nothing left
        f.batch(d, "Breakfast Idli", FoodCategory.COOKED_MEAL, 25, 0, 200);
        assertEquals(25, f.match().stream().mapToInt(Dispatch::getServings).sum());
    }

    @Test
    void unverifiedOrSuspendedRecipientsReceiveNothing() throws Exception {
        Donor d = f.donor();
        Recipient r = f.recipient("Shelter", NeedLevel.CRITICAL, 100);
        f.sys.users().setStatus(f.admin, r.getId(), AccountStatus.SUSPENDED);
        f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 40, 0, 200);
        assertEquals(0, f.match().size());
    }

    @Test
    void largeBatchIsSplitAcrossRecipients() throws Exception {
        Donor d = f.donor();
        f.recipient("A", NeedLevel.CRITICAL, 30, 1, 0);
        f.recipient("B", NeedLevel.HIGH, 30, 1.5, 0);
        f.recipient("C", NeedLevel.NORMAL, 30, 2, 0);
        FoodBatch b = f.batch(d, "Fest Dinner", FoodCategory.COOKED_MEAL, 80, 0, 240);
        List<Dispatch> ds = f.match();
        assertEquals(3, ds.size());
        assertEquals(80, ds.stream().mapToInt(Dispatch::getServings).sum());
        assertEquals(0, f.reload(b).getRemainingServings());
    }

    @Test
    void strategyCanBeSwitchedAtRuntime() throws Exception {
        f.sys.matching().configure(f.admin, "fifo", null);
        assertEquals("fifo", f.sys.matching().activeStrategy().id());
        Donor d = f.donor();
        f.recipient("Shelter", NeedLevel.HIGH, 100);
        f.batch(d, "Rice", FoodCategory.COOKED_MEAL, 10, 0, 200);
        assertEquals("fifo", f.match().get(0).getStrategy());
    }
}
