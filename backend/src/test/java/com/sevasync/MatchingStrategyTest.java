package com.sevasync;

import com.sevasync.matching.Allocation;
import com.sevasync.matching.ExpiryFirstStrategy;
import com.sevasync.matching.FifoStrategy;
import com.sevasync.matching.MatchContext;
import com.sevasync.matching.MatchingStrategy;
import com.sevasync.matching.RecipientSlot;
import com.sevasync.matching.SmartStrategy;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.model.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure algorithm tests: strategies get plain objects, no database. */
class MatchingStrategyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);
    private static final List<MatchingStrategy> ALL = List.of(new SmartStrategy(), new ExpiryFirstStrategy(),
            new FifoStrategy());

    private static Recipient recipient(String id, NeedLevel need, int capacity, GeoPoint at) {
        return new Recipient(id, id, id + "@x.y", "9000000000", "", at, RecipientType.SHELTER, need, capacity,
                AccountStatus.ACTIVE, NOW);
    }

    private static FoodBatch batch(String id, String donor, int servings, int minutesLeft, int createdAgo) {
        return new FoodBatch(id, donor, id, FoodCategory.COOKED_MEAL, true, servings, servings,
                NOW.minusMinutes(240 - minutesLeft), 240, BatchStatus.AVAILABLE, "", NOW.minusMinutes(createdAgo));
    }

    private static MatchContext ctx(Map<String, GeoPoint> donors) {
        return new MatchContext(NOW, donors, 10, VehicleType.BICYCLE);
    }

    @Test
    void noStrategyEverOverAllocatesABatchOrARecipient() {
        Random rnd = new Random(7);
        for (int round = 0; round < 200; round++) {
            Map<String, GeoPoint> donors = new HashMap<>();
            List<FoodBatch> batches = new ArrayList<>();
            List<RecipientSlot> slots = new ArrayList<>();
            for (int i = 0; i < 1 + rnd.nextInt(6); i++) {
                donors.put("D" + i, GeoPoint.CAMPUS.offsetKm(rnd.nextDouble(), rnd.nextDouble()));
            }
            for (int i = 0; i < rnd.nextInt(10); i++) {
                batches.add(batch("B" + i, "D" + rnd.nextInt(donors.size()), 1 + rnd.nextInt(100),
                        rnd.nextInt(240), rnd.nextInt(100)));
            }
            for (int i = 0; i < rnd.nextInt(8); i++) {
                Recipient r = recipient("R" + i, NeedLevel.values()[rnd.nextInt(3)], 1 + rnd.nextInt(80),
                        GeoPoint.CAMPUS.offsetKm(rnd.nextDouble() * 6, rnd.nextDouble() * 6));
                slots.add(new RecipientSlot(r, 1 + rnd.nextInt(r.getDailyCapacity()), rnd.nextInt(100)));
            }
            for (MatchingStrategy s : ALL) {
                List<Allocation> out = s.allocate(batches, slots, ctx(donors));
                Map<String, Integer> perBatch = new HashMap<>();
                Map<String, Integer> perRecipient = new HashMap<>();
                for (Allocation a : out) {
                    perBatch.merge(a.batchId(), a.servings(), Integer::sum);
                    perRecipient.merge(a.recipientId(), a.servings(), Integer::sum);
                }
                for (FoodBatch b : batches) {
                    assertTrue(perBatch.getOrDefault(b.getId(), 0) <= b.getRemainingServings(), s.id());
                    if (b.minutesRemaining(NOW) < 10) {
                        assertEquals(0, perBatch.getOrDefault(b.getId(), 0), s.id() + " used nearly-expired food");
                    }
                }
                for (RecipientSlot slot : slots) {
                    assertTrue(perRecipient.getOrDefault(slot.recipient().getId(), 0) <= slot.remaining(), s.id());
                }
            }
        }
    }

    @Test
    void strategiesDoNotMutateTheirInputs() {
        FoodBatch b = batch("B1", "D1", 50, 120, 5);
        RecipientSlot slot = new RecipientSlot(recipient("R1", NeedLevel.HIGH, 100, GeoPoint.CAMPUS), 100, 0);
        for (MatchingStrategy s : ALL) {
            s.allocate(List.of(b), List.of(slot), ctx(Map.of("D1", GeoPoint.CAMPUS)));
        }
        assertEquals(50, b.getRemainingServings());
        assertEquals(100, slot.remaining());
    }

    @Test
    void expiryFirstFeedsTheMostUrgentFoodToTheNeediestRecipient() {
        List<FoodBatch> batches = List.of(batch("LATER", "D", 30, 200, 50), batch("SOON", "D", 30, 40, 1));
        List<RecipientSlot> slots = List.of(
                new RecipientSlot(recipient("NORMAL", NeedLevel.NORMAL, 30, GeoPoint.CAMPUS), 30, 0),
                new RecipientSlot(recipient("CRIT", NeedLevel.CRITICAL, 30, GeoPoint.CAMPUS), 30, 0));
        List<Allocation> out = new ExpiryFirstStrategy().allocate(batches, slots, ctx(Map.of("D", GeoPoint.CAMPUS)));
        assertEquals("SOON", out.get(0).batchId());
        assertEquals("CRIT", out.get(0).recipientId());
    }

    @Test
    void smartPrefersTheNearerRecipientWhenNeedIsEqual() {
        GeoPoint donor = GeoPoint.CAMPUS;
        List<RecipientSlot> slots = List.of(
                new RecipientSlot(recipient("FAR", NeedLevel.HIGH, 50, donor.offsetKm(5, 0)), 50, 0),
                new RecipientSlot(recipient("NEAR", NeedLevel.HIGH, 50, donor.offsetKm(1, 0)), 50, 0));
        List<Allocation> out = new SmartStrategy().allocate(List.of(batch("B", "D", 20, 200, 1)), slots,
                ctx(Map.of("D", donor)));
        assertEquals("NEAR", out.get(0).recipientId());
    }

    @Test
    void smartSpreadsFoodToRecipientsThatReceivedLittleThisWeek() {
        GeoPoint donor = GeoPoint.CAMPUS;
        List<RecipientSlot> slots = List.of(
                new RecipientSlot(recipient("FED", NeedLevel.NORMAL, 50, donor.offsetKm(1, 0)), 50, 100),
                new RecipientSlot(recipient("HUNGRY", NeedLevel.NORMAL, 50, donor.offsetKm(1.2, 0)), 50, 0));
        List<Allocation> out = new SmartStrategy().allocate(List.of(batch("B", "D", 20, 200, 1)), slots,
                ctx(Map.of("D", donor)));
        assertEquals("HUNGRY", out.get(0).recipientId());
    }

    @Test
    void smartNeverSendsFoodWhereItCannotArriveInTime() {
        GeoPoint donor = GeoPoint.CAMPUS;
        // 12 km by bicycle takes ~1 h; the food has 45 minutes left
        List<RecipientSlot> slots = List.of(
                new RecipientSlot(recipient("FAR", NeedLevel.CRITICAL, 50, donor.offsetKm(12, 0)), 50, 0));
        List<Allocation> out = new SmartStrategy().allocate(List.of(batch("B", "D", 20, 45, 1)), slots,
                ctx(Map.of("D", donor)));
        assertTrue(out.isEmpty());
        // the distance-blind strategy would have sent it anyway
        assertEquals(1, new ExpiryFirstStrategy().allocate(List.of(batch("B", "D", 20, 45, 1)), slots,
                ctx(Map.of("D", donor))).size());
    }

    @Test
    void fifoServesInLoggingOrder() {
        List<FoodBatch> batches = List.of(batch("NEW", "D", 10, 40, 1), batch("OLD", "D", 10, 200, 90));
        List<RecipientSlot> slots = List.of(
                new RecipientSlot(recipient("R1", NeedLevel.NORMAL, 10, GeoPoint.CAMPUS), 10, 0));
        List<Allocation> out = new FifoStrategy().allocate(batches, slots, ctx(Map.of("D", GeoPoint.CAMPUS)));
        assertEquals("OLD", out.get(0).batchId(), "FIFO ignores urgency - which is exactly its weakness");
    }
}
