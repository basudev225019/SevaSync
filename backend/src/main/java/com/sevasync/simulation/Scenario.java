package com.sevasync.simulation;

import com.sevasync.model.AccountStatus;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.model.VehicleType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One synthetic campus day, generated from a seed so every strategy faces exactly the same food, recipients and
 * volunteers. Immutable after generation; {@link #freshBatches()} hands out new copies for each simulation.
 */
public final class Scenario {

    /** A batch template: when it appears and what it is. */
    record Arrival(LocalDateTime at, String id, String donorId, FoodCategory category, int servings,
                   LocalDateTime preparedAt, int shelfLifeMinutes) {
    }

    /**
     * Tunable scenario size. The default is a campus where food supply roughly matches recipient capacity but
     * volunteers are scarce - the situation in which the matching decision actually matters. (When supply far
     * exceeds capacity, every strategy wastes the same surplus: no algorithm can create room that does not exist.)
     */
    public record Config(int donors, int recipients, int volunteers, int batches) {
        public static Config defaults() {
            return new Config(6, 8, 2, 12);
        }
    }

    final LocalDateTime start;
    final LocalDateTime end;
    final Map<String, GeoPoint> donorLocations;
    final List<Recipient> recipients;
    final List<VehicleType> volunteers;
    final GeoPoint depot;
    final List<Arrival> arrivals;

    private Scenario(LocalDateTime start, LocalDateTime end, Map<String, GeoPoint> donorLocations,
                     List<Recipient> recipients, List<VehicleType> volunteers, GeoPoint depot, List<Arrival> arrivals) {
        this.start = start;
        this.end = end;
        this.donorLocations = donorLocations;
        this.recipients = recipients;
        this.volunteers = volunteers;
        this.depot = depot;
        this.arrivals = arrivals;
    }

    public static Scenario generate(long seed, Config cfg) {
        Random rnd = new Random(seed);
        GeoPoint centre = GeoPoint.CAMPUS;
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 8, 0);
        LocalDateTime end = start.plusHours(24);

        Map<String, GeoPoint> donors = new HashMap<>();
        for (int i = 1; i <= cfg.donors(); i++) {
            donors.put(String.format("DON-%02d", i), around(centre, 0.2, 1.5, rnd));
        }

        // need levels are shuffled so registration order (FIFO's tie-break) says nothing about urgency
        List<NeedLevel> needs = new ArrayList<>();
        for (int i = 0; i < cfg.recipients(); i++) {
            double r = rnd.nextDouble();
            needs.add(r < 0.25 ? NeedLevel.CRITICAL : r < 0.55 ? NeedLevel.HIGH : NeedLevel.NORMAL);
        }
        Collections.shuffle(needs, rnd);
        List<Recipient> recipients = new ArrayList<>();
        RecipientType[] types = RecipientType.values();
        for (int i = 1; i <= cfg.recipients(); i++) {
            recipients.add(new Recipient(String.format("REC-%02d", i), "Recipient " + i, "r" + i + "@sim.local",
                    "9000000000", "", around(centre, 1.0, 7.0, rnd), types[rnd.nextInt(types.length)],
                    needs.get(i - 1), 30 + rnd.nextInt(10) * 10, AccountStatus.ACTIVE, start));
        }

        List<VehicleType> fleet = new ArrayList<>();
        VehicleType[] choices = {VehicleType.BICYCLE, VehicleType.SCOOTER, VehicleType.WALK, VehicleType.BICYCLE};
        for (int i = 0; i < cfg.volunteers(); i++) {
            fleet.add(choices[rnd.nextInt(choices.length)]);
        }

        List<String> donorIds = new ArrayList<>(donors.keySet());
        Collections.sort(donorIds);
        List<Arrival> arrivals = new ArrayList<>();
        for (int i = 1; i <= cfg.batches(); i++) {
            LocalDateTime at = start.plusMinutes(rnd.nextInt(14 * 60));
            FoodCategory cat = category(rnd);
            int max = cat.getMaxShelfLifeMinutes();
            int shelf = Math.max(90, (int) (max * (0.35 + 0.65 * rnd.nextDouble())));
            int preparedAgo = rnd.nextInt(Math.max(1, Math.min(60, shelf - 60)));
            arrivals.add(new Arrival(at, String.format("BAT-%03d", i), donorIds.get(rnd.nextInt(donorIds.size())),
                    cat, 15 + rnd.nextInt(16) * 5, at.minusMinutes(preparedAgo), shelf));
        }
        arrivals.sort((a, b) -> a.at().compareTo(b.at()));
        return new Scenario(start, end, donors, recipients, fleet, centre, arrivals);
    }

    /** New mutable batch objects for one simulation run. */
    Map<String, FoodBatch> freshBatches() {
        Map<String, FoodBatch> out = new HashMap<>();
        for (Arrival a : arrivals) {
            out.put(a.id(), new FoodBatch(a.id(), a.donorId(), "Simulated " + a.category(), a.category(), true,
                    a.servings(), a.servings(), a.preparedAt(), a.shelfLifeMinutes(), BatchStatus.AVAILABLE, "",
                    a.at()));
        }
        return out;
    }

    private static FoodCategory category(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.55) {
            return FoodCategory.COOKED_MEAL;
        } else if (r < 0.75) {
            return FoodCategory.BAKERY;
        } else if (r < 0.88) {
            return FoodCategory.FRUITS_VEGETABLES;
        } else if (r < 0.96) {
            return FoodCategory.BEVERAGE;
        }
        return FoodCategory.PACKAGED;
    }

    private static GeoPoint around(GeoPoint c, double minKm, double maxKm, Random rnd) {
        double angle = rnd.nextDouble() * 2 * Math.PI;
        double r = minKm + rnd.nextDouble() * (maxKm - minKm);
        return c.offsetKm(r * Math.sin(angle), r * Math.cos(angle));
    }
}
