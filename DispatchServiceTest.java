package com.sevasync;

import com.sevasync.exception.CapacityExceededException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.service.ImpactReportService.ImpactReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchServiceTest {
    private Fixture f;
    private String donor;
    private Recipient recipient;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture();
        donor = f.donor();
        recipient = f.recipient("Asha Home", NeedLevel.HIGH, 100);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    @Test
    void deliveryCompletesBatch() throws Exception {
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 10, 180);
        Dispatch d = f.sys.matching().runMatching().dispatches().get(0);

        Dispatch delivered = f.sys.dispatches().markDelivered(d.getId(), "Ravi");

        assertEquals(DispatchStatus.DELIVERED, delivered.getStatus());
        assertEquals("Ravi", delivered.getVolunteer());
        assertEquals(BatchStatus.COMPLETED, f.sys.donations().getBatch(b.getId()).getStatus());
        assertThrows(InvalidInputException.class, () -> f.sys.dispatches().markDelivered(d.getId(), "Ravi"));
    }

    @Test
    void deliveryAfterExpiryIsRefusedAndCancelled() throws Exception {
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 10, 60);
        Dispatch d = f.sys.matching().runMatching().dispatches().get(0);
        f.clock.advance(Duration.ofMinutes(55));          // 10 + 55 > 60 -> expired

        assertThrows(ExpiredBatchException.class, () -> f.sys.dispatches().markDelivered(d.getId(), "Ravi"));

        assertEquals(DispatchStatus.CANCELLED, f.sys.dispatches().get(d.getId()).getStatus());
        FoodBatch stored = f.sys.donations().getBatch(b.getId());
        assertEquals(BatchStatus.EXPIRED, stored.getStatus());
        assertEquals(40, stored.getRemainingServings());   // wasted servings
    }

    @Test
    void cancelReturnsServingsToThePool() throws Exception {
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 10, 180);
        Dispatch d = f.sys.matching().runMatching().dispatches().get(0);
        assertEquals(BatchStatus.ALLOCATED, f.sys.donations().getBatch(b.getId()).getStatus());

        f.sys.dispatches().cancel(d.getId(), "Volunteer unavailable");

        FoodBatch stored = f.sys.donations().getBatch(b.getId());
        assertEquals(BatchStatus.AVAILABLE, stored.getStatus());
        assertEquals(40, stored.getRemainingServings());
        assertEquals(100, f.sys.recipients().remainingCapacityToday(recipient));
    }

    @Test
    void manualAssignmentEnforcesCapacityRules() throws Exception {
        FoodBatch b = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 150, 10, 180);
        assertThrows(CapacityExceededException.class,
                () -> f.sys.dispatches().assignManually(b.getId(), recipient.getId(), 101));   // > recipient capacity
        f.sys.dispatches().assignManually(b.getId(), recipient.getId(), 100);
        assertThrows(CapacityExceededException.class,
                () -> f.sys.dispatches().assignManually(b.getId(), recipient.getId(), 1));     // recipient full
        Recipient other = f.recipient("Other", NeedLevel.NORMAL, 500);
        assertThrows(CapacityExceededException.class,
                () -> f.sys.dispatches().assignManually(b.getId(), other.getId(), 51));        // batch has 50 left
        assertThrows(EntityNotFoundException.class,
                () -> f.sys.dispatches().assignManually("BAT-9999", other.getId(), 1));
    }

    @Test
    void impactReportAddsUp() throws Exception {
        FoodBatch b1 = f.batch(donor, "Rice", FoodCategory.COOKED_MEAL, 40, 10, 180);   // expires 14:50
        FoodBatch b2 = f.batch(donor, "Curry", FoodCategory.COOKED_MEAL, 70, 10, 60);   // expires 12:50 (earliest)
        f.recipient("Second", NeedLevel.NORMAL, 30);
        f.sys.matching().runMatching();   // b2 -> Asha 70, b1 -> Asha 30, b1 -> Second 10

        Dispatch toDeliver = null;
        for (Dispatch d : f.sys.dispatches().list(DispatchStatus.ASSIGNED)) {
            if (d.getBatchId().equals(b1.getId()) && d.getRecipientId().equals(recipient.getId())) {
                toDeliver = d;
            }
        }
        f.sys.dispatches().markDelivered(toDeliver.getId(), "Ravi");
        f.clock.advance(Duration.ofMinutes(55));          // b2 expires, its 70-serving dispatch is cancelled

        assertEquals(1, f.sys.dispatches().expireOverdue());
        ImpactReport r = f.sys.reports().generate();

        assertEquals(b1.getServings() + b2.getServings(), r.servingsDonated());
        assertEquals(30, r.servingsDelivered());
        assertEquals(10, r.servingsInTransit());
        assertEquals(70, r.servingsWasted());
        assertEquals(r.servingsDonated(), r.servingsDelivered() + r.servingsInTransit()
                + r.servingsAvailable() + r.servingsWasted());
        assertTrue(r.foodSavedKg() > 0);
        assertTrue(r.format().contains("SevaSync Impact Report"));
    }
}
