package com.sevasync.service;

import com.sevasync.model.Dispatch;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.LocatedUser;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteUserRepository;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the rich JSON views the frontend needs (a dispatch with its food, donor, recipient and volunteer; a batch
 * with its freshness) from plain entities. Loads related rows in bulk so a list never costs one query per item.
 */
public class ViewAssembler {
    private final SqliteUserRepository users;
    private final SqliteFoodBatchRepository batches;
    private final Clock clock;

    public ViewAssembler(SqliteUserRepository users, SqliteFoodBatchRepository batches, Clock clock) {
        this.users = users;
        this.batches = batches;
        this.clock = clock;
    }

    public Map<String, Object> batch(FoodBatch b) {
        return batches(List.of(b)).get(0);
    }

    public List<Map<String, Object>> batches(Collection<FoodBatch> list) {
        Map<String, User> byId = usersById();
        LocalDateTime now = TimeUtil.now(clock);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (FoodBatch b : list) {
            out.add(batchView(b, byId.get(b.getDonorId()), now));
        }
        return out;
    }

    public Map<String, Object> dispatch(Dispatch d) {
        return dispatches(List.of(d), null).get(0);
    }

    /**
     * @param viewer when a volunteer is looking, adds their distance to the pickup and a trip estimate
     */
    public List<Map<String, Object>> dispatches(Collection<Dispatch> list, Volunteer viewer) {
        Map<String, User> byId = usersById();
        List<String> batchIds = list.stream().map(Dispatch::getBatchId).toList();
        Map<String, FoodBatch> batchById = batches.findByIds(batchIds);
        LocalDateTime now = TimeUtil.now(clock);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Dispatch d : list) {
            Map<String, Object> m = d.toMap();
            FoodBatch b = batchById.get(d.getBatchId());
            User donor = b == null ? null : byId.get(b.getDonorId());
            User recipient = byId.get(d.getRecipientId());
            User volunteer = d.getVolunteerId() == null ? null : byId.get(d.getVolunteerId());
            if (b != null) {
                m.put("batch", batchView(b, donor, now));
            }
            m.put("donor", donor == null ? null : donor.toPublicMap());
            m.put("recipient", recipient == null ? null : recipient.toPublicMap());
            m.put("volunteer", volunteer == null ? null : volunteer.toPublicMap());
            if (viewer != null && donor instanceof LocatedUser ld && recipient instanceof LocatedUser lr) {
                double toPickup = viewer.distanceKmTo(ld);
                double trip = toPickup + d.getDistanceKm();
                m.put("distanceToPickupKm", round2(toPickup));
                m.put("tripKm", round2(trip));
                m.put("etaMinutes", viewer.getVehicle().tripMinutes(trip));
                m.put("fitsVehicle", viewer.canCarry(d.getServings()));
            }
            out.add(m);
        }
        return out;
    }

    public Map<String, User> usersById() {
        Map<String, User> byId = new HashMap<>();
        for (User u : users.findAll()) {
            byId.put(u.getId(), u);
        }
        return byId;
    }

    private static Map<String, Object> batchView(FoodBatch b, User donor, LocalDateTime now) {
        Map<String, Object> m = b.toMap();
        m.put("donor", donor == null ? null : donor.toPublicMap());
        m.put("minutesLeft", Math.max(0, b.minutesRemaining(now)));
        m.put("freshness", round2(b.freshness(now)));
        m.put("kg", round2(b.kgFor(b.getServings())));
        return m;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
