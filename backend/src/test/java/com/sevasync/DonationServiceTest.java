package com.sevasync;

import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.IllegalTransitionException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonationServiceTest {
    private final Fixture f = new Fixture();

    @AfterEach
    void tearDown() {
        f.close();
    }

    @Test
    void loggedBatchIsStoredWithDerivedExpiryAndAudited() throws Exception {
        Donor d = f.donor();
        FoodBatch b = f.batch(d, "Veg Biryani", FoodCategory.COOKED_MEAL, 80, 30, 180);
        FoodBatch stored = f.reload(b);
        assertEquals(BatchStatus.AVAILABLE, stored.getStatus());
        assertEquals(f.now().minusMinutes(30).plusMinutes(180), stored.getExpiresAt());
        assertTrue(f.sys.audit().tail(5, "FoodBatch", b.getId()).stream()
                .anyMatch(e -> e.action().equals("BATCH_LOGGED")));
    }

    @Test
    void unsafeBatchesNeverEnterTheSystem() throws Exception {
        Donor d = f.donor();
        assertThrows(UnsafeFoodException.class, () -> f.batch(d, "Old Curry", FoodCategory.COOKED_MEAL, 10, 0, 600));
        assertEquals(0, f.sys.batchRepo().count());
    }

    @Test
    void donorsOnlySeeTheirOwnBatches() throws Exception {
        Donor a = f.donor("Mess A", 0, 0);
        Donor b = f.donor("Mess B", 0.2, 0.2);
        f.batch(a, "Rice", FoodCategory.COOKED_MEAL, 10, 0, 120);
        FoodBatch theirs = f.batch(b, "Bread", FoodCategory.BAKERY, 10, 0, 300);
        assertEquals(1, f.sys.donations().list(a, null).size());
        assertThrows(AccessDeniedException.class, () -> f.sys.donations().get(a, theirs.getId()));
        assertEquals(2, f.sys.donations().list(f.admin, null).size());
    }

    @Test
    void withdrawingCancelsOpenMatchesButNotOnceAVolunteerIsOnTheWay() throws Exception {
        Donor d = f.donor();
        f.recipient("Shelter", NeedLevel.HIGH, 100);
        FoodBatch b = f.batch(d, "Poha", FoodCategory.COOKED_MEAL, 20, 0, 200);
        List<Dispatch> ds = f.match();
        f.sys.donations().withdraw(d, b.getId(), "eaten at dinner");
        assertEquals(BatchStatus.WITHDRAWN, f.reload(b).getStatus());
        assertEquals(DispatchStatus.CANCELLED, f.reload(ds.get(0)).getStatus());

        FoodBatch c = f.batch(d, "Upma", FoodCategory.COOKED_MEAL, 20, 0, 200);
        Dispatch m = f.match().get(0);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);
        f.sys.dispatches().claim(v, m.getId());
        assertThrows(IllegalTransitionException.class, () -> f.sys.donations().withdraw(d, c.getId(), ""));
    }

    @Test
    void autoMatchingReactsToNewFood() throws Exception {
        f.sys.matching().configure(f.admin, null, true);
        Donor d = f.donor();
        f.recipient("Shelter", NeedLevel.CRITICAL, 100);
        f.batch(d, "Chapati", FoodCategory.COOKED_MEAL, 30, 0, 200);
        assertEquals(1, f.sys.dispatchRepo().findByStatus(DispatchStatus.ASSIGNED).size());
    }

    @Test
    void expiryMonitorExpiresUnclaimedFood() throws Exception {
        Donor d = f.donor();
        FoodBatch b = f.batch(d, "Sambar Rice", FoodCategory.COOKED_MEAL, 15, 0, 60);
        f.clock.advance(Duration.ofMinutes(61));
        f.sys.monitor().tick();
        FoodBatch after = f.reload(b);
        assertEquals(BatchStatus.EXPIRED, after.getStatus());
        assertEquals(15, after.getRemainingServings());
    }
}
