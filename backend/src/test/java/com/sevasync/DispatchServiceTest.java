package com.sevasync;

import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.CapacityExceededException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.IllegalTransitionException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The volunteer workflow end to end through the services and the real database. */
class DispatchServiceTest {
    private final Fixture f = new Fixture();

    @AfterEach
    void tearDown() {
        f.close();
    }

    private record Setup(Donor donor, Recipient recipient, FoodBatch batch, Dispatch dispatch) {
    }

    private Setup oneMatch(int servings) throws SevaSyncException {
        Donor d = f.donor();
        Recipient r = f.recipient("Shelter", NeedLevel.HIGH, 200);
        FoodBatch b = f.batch(d, "Dal Rice", FoodCategory.COOKED_MEAL, servings, 10, 240);
        List<Dispatch> created = f.match();
        assertEquals(1, created.size());
        return new Setup(d, r, b, created.get(0));
    }

    @Test
    void fullDeliveryCompletesTheBatchAndRecordsTheRating() throws Exception {
        Setup s = oneMatch(30);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);

        f.sys.dispatches().claim(v, s.dispatch().getId());
        f.clock.advance(Duration.ofMinutes(8));
        f.sys.dispatches().pickUp(v, s.dispatch().getId());
        f.clock.advance(Duration.ofMinutes(15));
        f.sys.dispatches().deliver(v, s.dispatch().getId());
        assertEquals(BatchStatus.COMPLETED, f.reload(s.batch()).getStatus());

        f.sys.dispatches().confirmReceipt(s.recipient(), s.dispatch().getId(), 5, "Still warm");
        Dispatch done = f.reload(s.dispatch());
        assertEquals(DispatchStatus.DELIVERED, done.getStatus());
        assertEquals(5, done.getRating());
        assertNotNull(done.getConfirmedAt());
        assertEquals(30, ((Number) f.sys.reports().forUser(v).get("servingsDelivered")).intValue());
    }

    @Test
    void vehicleSizeAndConcurrentPickupsAreLimited() throws Exception {
        Setup big = oneMatch(60);
        Volunteer walker = f.volunteer("Rahul", VehicleType.WALK);
        assertThrows(CapacityExceededException.class, () -> f.sys.dispatches().claim(walker, big.dispatch().getId()));

        Volunteer car = f.volunteer("Meera", VehicleType.CAR);
        f.sys.dispatches().claim(car, big.dispatch().getId());
        Recipient r2 = f.recipient("Kitchen", NeedLevel.NORMAL, 500);
        for (int i = 0; i < 3; i++) {
            f.batch(big.donor(), "Snack " + i, FoodCategory.BAKERY, 10, 5, 300);
        }
        List<Dispatch> more = f.match();
        assertTrue(more.size() >= 3);
        f.sys.dispatches().claim(car, more.get(0).getId());
        f.sys.dispatches().claim(car, more.get(1).getId());
        assertThrows(CapacityExceededException.class, () -> f.sys.dispatches().claim(car, more.get(2).getId()));
        assertNotNull(r2);
    }

    @Test
    void anotherVolunteerCannotTakeOverAClaimedPickup() throws Exception {
        Setup s = oneMatch(20);
        Volunteer a = f.volunteer("A", VehicleType.BICYCLE);
        Volunteer b = f.volunteer("B", VehicleType.BICYCLE);
        f.sys.dispatches().claim(a, s.dispatch().getId());
        assertThrows(IllegalTransitionException.class, () -> f.sys.dispatches().claim(b, s.dispatch().getId()));
        assertThrows(IllegalTransitionException.class, () -> f.sys.dispatches().pickUp(b, s.dispatch().getId()));
        f.sys.dispatches().release(a, s.dispatch().getId());
        f.sys.dispatches().claim(b, s.dispatch().getId());
    }

    @Test
    void exactlyOneOfManySimultaneousClaimsWins() throws Exception {
        Setup s = oneMatch(20);
        int n = 8;
        List<Volunteer> crew = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            crew.add(f.volunteer("V" + i, VehicleType.SCOOTER));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (Volunteer v : crew) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    f.sys.dispatches().claim(v, s.dispatch().getId());
                    wins.incrementAndGet();
                } catch (IllegalTransitionException expected) {
                    // lost the race
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> fut : futures) {
            fut.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(1, wins.get());
    }

    @Test
    void foodThatExpiresOnTheWayIsNeverHandedOver() throws Exception {
        Setup s = oneMatch(20);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);
        f.sys.dispatches().claim(v, s.dispatch().getId());
        f.sys.dispatches().pickUp(v, s.dispatch().getId());
        f.clock.advance(Duration.ofMinutes(240));
        assertThrows(ExpiredBatchException.class, () -> f.sys.dispatches().deliver(v, s.dispatch().getId()));
        assertEquals(DispatchStatus.CANCELLED, f.reload(s.dispatch()).getStatus());
        FoodBatch b = f.reload(s.batch());
        assertEquals(BatchStatus.EXPIRED, b.getStatus());
        assertEquals(20, b.getRemainingServings(), "all 20 servings count as wasted");
    }

    @Test
    void cancellingBeforePickupReturnsTheFoodToThePool() throws Exception {
        Setup s = oneMatch(25);
        f.sys.dispatches().cancel(f.admin, s.dispatch().getId(), "Recipient closed today");
        FoodBatch b = f.reload(s.batch());
        assertEquals(BatchStatus.AVAILABLE, b.getStatus());
        assertEquals(25, b.getRemainingServings());
    }

    @Test
    void onlyTheAddressedRecipientMayConfirm() throws Exception {
        Setup s = oneMatch(20);
        Recipient other = f.recipient("Other", NeedLevel.NORMAL, 50);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);
        f.sys.dispatches().claim(v, s.dispatch().getId());
        f.sys.dispatches().pickUp(v, s.dispatch().getId());
        f.sys.dispatches().deliver(v, s.dispatch().getId());
        assertThrows(AccessDeniedException.class,
                () -> f.sys.dispatches().confirmReceipt(other, s.dispatch().getId(), 5, ""));
    }

    @Test
    void rolesOnlyReachTheirOwnActions() throws Exception {
        Setup s = oneMatch(20);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);
        assertThrows(AccessDeniedException.class, () -> f.sys.dispatches().cancel(v, s.dispatch().getId(), ""));
        assertThrows(AccessDeniedException.class, () -> f.sys.matching().run(v, null));
        assertThrows(AccessDeniedException.class, () -> f.sys.dispatches().cancel(s.recipient(),
                s.dispatch().getId(), ""));
        // a donor sees dispatches of their own food, and only those
        Donor other = f.donor("Other Mess", 0.1, 0.1);
        assertEquals(1, f.sys.dispatches().listFor(s.donor(), null).size());
        assertEquals(0, f.sys.dispatches().listFor(other, null).size());
        assertThrows(AccessDeniedException.class, () -> f.sys.dispatches().get(other, s.dispatch().getId()));
    }

    @Test
    void manualAssignmentRespectsCapacity() throws Exception {
        Donor d = f.donor();
        Recipient small = f.recipient("Small Home", NeedLevel.CRITICAL, 10);
        FoodBatch b = f.batch(d, "Khichdi", FoodCategory.COOKED_MEAL, 40, 5, 240);
        assertThrows(CapacityExceededException.class,
                () -> f.sys.dispatches().assignManually(f.admin, b.getId(), small.getId(), 15));
        Dispatch ok = f.sys.dispatches().assignManually(f.admin, b.getId(), small.getId(), 10);
        assertEquals("manual", ok.getStrategy());
        assertEquals(30, f.reload(b).getRemainingServings());
    }

    @Test
    void openPickupBoardHidesClaimedAndExpiredWork() throws Exception {
        Setup s = oneMatch(20);
        Volunteer v = f.volunteer("Arjun", VehicleType.BICYCLE);
        assertEquals(1, f.sys.dispatches().openPickups(v).size());
        f.sys.dispatches().claim(v, s.dispatch().getId());
        assertEquals(0, f.sys.dispatches().openPickups(v).size());
    }
}
