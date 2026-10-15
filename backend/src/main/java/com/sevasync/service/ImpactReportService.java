package com.sevasync.service;

import com.sevasync.model.AccountStatus;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteUserRepository;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Impact and accountability figures: food rescued, CO2e avoided, waste, delivery speed, leaderboards and a daily
 * timeline. Figures are computed from the stored batches and dispatches, never stored separately, so they can
 * never drift from the data.
 */
public class ImpactReportService {
    /** kg CO2-equivalent avoided per kg of food that is eaten instead of landfilled (planning estimate). */
    public static final double CO2E_PER_KG = 2.5;
    public static final int TIMELINE_DAYS = 14;

    private final SqliteUserRepository users;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final Clock clock;

    public ImpactReportService(SqliteUserRepository users, SqliteFoodBatchRepository batches,
                               SqliteDispatchRepository dispatches, Clock clock) {
        this.users = users;
        this.batches = batches;
        this.dispatches = dispatches;
        this.clock = clock;
    }

    /** Full report for coordinators. */
    public Map<String, Object> overall() {
        List<FoodBatch> allBatches = batches.findAll();
        List<Dispatch> allDispatches = dispatches.findAll();
        Map<String, FoodBatch> batchById = index(allBatches);
        Map<String, User> userById = new HashMap<>();
        users.findAll().forEach(u -> userById.put(u.getId(), u));

        Totals t = totals(allBatches, allDispatches, batchById);
        Map<String, Object> m = t.toMap();
        m.put("generatedAt", TimeUtil.now(clock));
        m.put("donors", users.countByRoleAndStatus(Role.DONOR, AccountStatus.ACTIVE));
        m.put("recipients", users.countByRoleAndStatus(Role.RECIPIENT, AccountStatus.ACTIVE));
        m.put("volunteers", users.countByRoleAndStatus(Role.VOLUNTEER, AccountStatus.ACTIVE));
        m.put("pendingVerifications", users.countByRoleAndStatus(Role.DONOR, AccountStatus.PENDING)
                + users.countByRoleAndStatus(Role.RECIPIENT, AccountStatus.PENDING));

        Map<String, Integer> byDonor = new HashMap<>();
        Map<String, Integer> byRecipient = new HashMap<>();
        Map<String, Integer> byVolunteer = new HashMap<>();
        Map<String, Double> kmByVolunteer = new HashMap<>();
        for (Dispatch d : allDispatches) {
            if (d.getStatus() == DispatchStatus.DELIVERED) {
                FoodBatch b = batchById.get(d.getBatchId());
                if (b != null) {
                    byDonor.merge(b.getDonorId(), d.getServings(), Integer::sum);
                }
                byRecipient.merge(d.getRecipientId(), d.getServings(), Integer::sum);
                if (d.getVolunteerId() != null) {
                    byVolunteer.merge(d.getVolunteerId(), d.getServings(), Integer::sum);
                    kmByVolunteer.merge(d.getVolunteerId(), d.getDistanceKm(), Double::sum);
                }
            }
        }
        m.put("topDonors", rank(byDonor, userById, null, 5));
        m.put("topRecipients", rank(byRecipient, userById, null, 5));
        m.put("topVolunteers", rank(byVolunteer, userById, kmByVolunteer, 5));
        m.put("timeline", timeline(allBatches, allDispatches, batchById));
        m.put("categories", categories(allBatches, allDispatches, batchById));
        m.put("strategies", strategyBreakdown(allDispatches));
        return m;
    }

    /** Headline numbers for the public landing page: no names, no per-party data. */
    public Map<String, Object> publicStats() {
        List<FoodBatch> allBatches = batches.findAll();
        List<Dispatch> allDispatches = dispatches.findAll();
        Totals t = totals(allBatches, allDispatches, index(allBatches));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mealsServed", t.delivered);
        m.put("foodSavedKg", round1(t.kgSaved));
        m.put("co2AvoidedKg", round1(t.kgSaved * CO2E_PER_KG));
        m.put("deliveries", t.deliveries);
        m.put("donors", users.countByRoleAndStatus(Role.DONOR, AccountStatus.ACTIVE));
        m.put("recipients", users.countByRoleAndStatus(Role.RECIPIENT, AccountStatus.ACTIVE));
        m.put("volunteers", users.countByRoleAndStatus(Role.VOLUNTEER, AccountStatus.ACTIVE));
        m.put("deliveryRatePercent", t.deliveryRate());
        return m;
    }

    /** Personal impact for one participant, shaped by their role. */
    public Map<String, Object> forUser(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", user.getRole());
        switch (user.getRole()) {
            case DONOR -> {
                List<FoodBatch> own = batches.findByDonor(user.getId());
                List<Dispatch> ds = dispatches.findByDonor(user.getId());
                Totals t = totals(own, ds, index(own));
                m.putAll(t.toMap());
                m.put("averageRating", averageRating(ds));
                m.put("timeline", timeline(own, ds, index(own)));
            }
            case RECIPIENT -> {
                List<Dispatch> ds = dispatches.findByRecipient(user.getId());
                Map<String, FoodBatch> involved = batches.findByIds(ds.stream().map(Dispatch::getBatchId).toList());
                int received = 0;
                double kg = 0;
                int upcoming = 0;
                for (Dispatch d : ds) {
                    if (d.getStatus() == DispatchStatus.DELIVERED) {
                        received += d.getServings();
                        FoodBatch b = involved.get(d.getBatchId());
                        kg += b == null ? 0 : b.kgFor(d.getServings());
                    } else if (d.isActive()) {
                        upcoming += d.getServings();
                    }
                }
                m.put("servingsReceived", received);
                m.put("deliveriesReceived", ds.stream().filter(d -> d.getStatus() == DispatchStatus.DELIVERED).count());
                m.put("servingsIncoming", upcoming);
                m.put("foodReceivedKg", round1(kg));
                m.put("awaitingConfirmation", ds.stream()
                        .filter(d -> d.getStatus() == DispatchStatus.DELIVERED && d.getConfirmedAt() == null).count());
            }
            case VOLUNTEER -> {
                List<Dispatch> ds = dispatches.findByVolunteer(user.getId());
                Map<String, FoodBatch> involved = batches.findByIds(ds.stream().map(Dispatch::getBatchId).toList());
                int servings = 0;
                double km = 0;
                double kg = 0;
                long minutes = 0;
                int trips = 0;
                for (Dispatch d : ds) {
                    if (d.getStatus() == DispatchStatus.DELIVERED) {
                        trips++;
                        servings += d.getServings();
                        km += d.getDistanceKm();
                        FoodBatch b = involved.get(d.getBatchId());
                        kg += b == null ? 0 : b.kgFor(d.getServings());
                        if (d.getClaimedAt() != null && d.getDeliveredAt() != null) {
                            minutes += Duration.between(d.getClaimedAt(), d.getDeliveredAt()).toMinutes();
                        }
                    }
                }
                m.put("deliveries", trips);
                m.put("servingsDelivered", servings);
                m.put("distanceKm", round1(km));
                m.put("foodSavedKg", round1(kg));
                m.put("co2AvoidedKg", round1(kg * CO2E_PER_KG));
                m.put("averageTripMinutes", trips == 0 ? 0 : minutes / trips);
                m.put("activePickups", ds.stream().filter(Dispatch::isActive).count());
                m.put("averageRating", averageRating(ds));
                if (user instanceof Volunteer v) {
                    m.put("vehicle", v.getVehicle());
                }
            }
            case ADMIN -> m.putAll(publicStats());
        }
        return m;
    }

    // ------------------------------------------------------------------ calculations

    private static final class Totals {
        int batches;
        int donated;
        int delivered;
        int inTransit;
        int available;
        int wasted;
        int deliveries;
        double kgSaved;
        double kgWasted;
        long deliveryMinutesTotal;
        int timedDeliveries;

        double deliveryRate() {
            int settled = delivered + wasted;
            return settled == 0 ? 0 : round1(delivered * 100.0 / settled);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batches", batches);
            m.put("servingsDonated", donated);
            m.put("servingsDelivered", delivered);
            m.put("servingsInTransit", inTransit);
            m.put("servingsAvailable", available);
            m.put("servingsWasted", wasted);
            m.put("deliveries", deliveries);
            m.put("deliveryRatePercent", deliveryRate());
            m.put("foodSavedKg", round1(kgSaved));
            m.put("foodWastedKg", round1(kgWasted));
            m.put("co2AvoidedKg", round1(kgSaved * CO2E_PER_KG));
            m.put("averageDeliveryMinutes", timedDeliveries == 0 ? 0 : deliveryMinutesTotal / timedDeliveries);
            return m;
        }
    }

    private static Totals totals(List<FoodBatch> bs, List<Dispatch> ds, Map<String, FoodBatch> batchById) {
        Totals t = new Totals();
        Map<String, Integer> deliveredByBatch = new HashMap<>();
        for (Dispatch d : ds) {
            FoodBatch b = batchById.get(d.getBatchId());
            if (d.getStatus() == DispatchStatus.DELIVERED) {
                t.delivered += d.getServings();
                t.deliveries++;
                t.kgSaved += b == null ? 0 : b.kgFor(d.getServings());
                deliveredByBatch.merge(d.getBatchId(), d.getServings(), Integer::sum);
                if (d.getDeliveredAt() != null) {
                    t.deliveryMinutesTotal += Duration.between(d.getAssignedAt(), d.getDeliveredAt()).toMinutes();
                    t.timedDeliveries++;
                }
            } else if (d.isActive()) {
                t.inTransit += d.getServings();
            }
        }
        for (FoodBatch b : bs) {
            t.batches++;
            t.donated += b.getServings();
            if (b.getStatus() == BatchStatus.AVAILABLE) {
                t.available += b.getRemainingServings();
            } else if (b.getStatus() == BatchStatus.EXPIRED) {
                int lost = b.getServings() - deliveredByBatch.getOrDefault(b.getId(), 0);
                t.wasted += lost;
                t.kgWasted += b.kgFor(lost);
            }
        }
        return t;
    }

    private List<Map<String, Object>> timeline(List<FoodBatch> bs, List<Dispatch> ds,
                                               Map<String, FoodBatch> batchById) {
        LocalDate today = TimeUtil.now(clock).toLocalDate();
        LocalDate first = today.minusDays(TIMELINE_DAYS - 1L);
        TreeMap<LocalDate, int[]> days = new TreeMap<>(); // donated, delivered, wasted
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            days.put(d, new int[3]);
        }
        for (FoodBatch b : bs) {
            int[] row = days.get(b.getCreatedAt().toLocalDate());
            if (row != null) {
                row[0] += b.getServings();
            }
            if (b.getStatus() == BatchStatus.EXPIRED) {
                int[] w = days.get(b.getExpiresAt().toLocalDate());
                if (w != null) {
                    w[2] += b.getRemainingServings();
                }
            }
        }
        for (Dispatch d : ds) {
            if (d.getStatus() == DispatchStatus.DELIVERED && d.getDeliveredAt() != null) {
                int[] row = days.get(d.getDeliveredAt().toLocalDate());
                if (row != null) {
                    row[1] += d.getServings();
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        days.forEach((day, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("donated", v[0]);
            m.put("delivered", v[1]);
            m.put("wasted", v[2]);
            out.add(m);
        });
        return out;
    }

    private static List<Map<String, Object>> categories(List<FoodBatch> bs, List<Dispatch> ds,
                                                        Map<String, FoodBatch> batchById) {
        Map<FoodCategory, int[]> agg = new EnumMap<>(FoodCategory.class); // donated, delivered
        for (FoodCategory c : FoodCategory.values()) {
            agg.put(c, new int[2]);
        }
        for (FoodBatch b : bs) {
            agg.get(b.getCategory())[0] += b.getServings();
        }
        for (Dispatch d : ds) {
            FoodBatch b = batchById.get(d.getBatchId());
            if (b != null && d.getStatus() == DispatchStatus.DELIVERED) {
                agg.get(b.getCategory())[1] += d.getServings();
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((c, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c);
            m.put("donated", v[0]);
            m.put("delivered", v[1]);
            out.add(m);
        });
        return out;
    }

    private static List<Map<String, Object>> strategyBreakdown(List<Dispatch> ds) {
        Map<String, double[]> agg = new LinkedHashMap<>(); // dispatches, servings, km
        for (Dispatch d : ds) {
            if (d.getStatus() == DispatchStatus.CANCELLED) {
                continue;
            }
            double[] v = agg.computeIfAbsent(d.getStrategy().isEmpty() ? "unknown" : d.getStrategy(),
                    k -> new double[3]);
            v[0]++;
            v[1] += d.getServings();
            v[2] += d.getDistanceKm();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((s, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("strategy", s);
            m.put("dispatches", (int) v[0]);
            m.put("servings", (int) v[1]);
            m.put("averageDistanceKm", v[0] == 0 ? 0 : round1(v[2] / v[0]));
            out.add(m);
        });
        return out;
    }

    private static List<Map<String, Object>> rank(Map<String, Integer> totals, Map<String, User> users,
                                                  Map<String, Double> km, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        totals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(e -> {
                    User u = users.get(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getKey());
                    m.put("name", u == null ? e.getKey() : u.toPublicMap().get("name"));
                    m.put("servings", e.getValue());
                    if (km != null) {
                        m.put("distanceKm", round1(km.getOrDefault(e.getKey(), 0.0)));
                    }
                    out.add(m);
                });
        return out;
    }

    private static Double averageRating(List<Dispatch> ds) {
        double sum = 0;
        int n = 0;
        for (Dispatch d : ds) {
            if (d.getRating() > 0) {
                sum += d.getRating();
                n++;
            }
        }
        return n == 0 ? null : round1(sum / n);
    }

    private static Map<String, FoodBatch> index(List<FoodBatch> bs) {
        Map<String, FoodBatch> m = new HashMap<>();
        for (FoodBatch b : bs) {
            m.put(b.getId(), b);
        }
        return m;
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
