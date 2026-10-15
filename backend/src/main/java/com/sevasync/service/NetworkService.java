package com.sevasync.service;

import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.Recipient;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteDonorRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteRecipientRepository;
import com.sevasync.storage.SqliteVolunteerRepository;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live snapshot of the whole redistribution network - donors, recipients, volunteers, open food and the routes
 * between them - shaped for the 3D campus view. The public variant (landing page) leaves out volunteers entirely.
 */
public class NetworkService {
    private static final int RECENT_DELIVERIES = 12;

    private final SqliteDonorRepository donors;
    private final SqliteRecipientRepository recipients;
    private final SqliteVolunteerRepository volunteers;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final RecipientService capacity;
    private final Clock clock;

    public NetworkService(SqliteDonorRepository donors, SqliteRecipientRepository recipients,
                          SqliteVolunteerRepository volunteers, SqliteFoodBatchRepository batches,
                          SqliteDispatchRepository dispatches, RecipientService capacity, Clock clock) {
        this.donors = donors;
        this.recipients = recipients;
        this.volunteers = volunteers;
        this.batches = batches;
        this.dispatches = dispatches;
        this.capacity = capacity;
        this.clock = clock;
    }

    /** @param viewer signed-in user, or {@code null} for the anonymous public view */
    public Map<String, Object> snapshot(User viewer) {
        boolean full = viewer != null;
        LocalDateTime now = TimeUtil.now(clock);
        List<FoodBatch> open = batches.findOpen();
        Map<String, FoodBatch> batchById = new HashMap<>();
        Map<String, int[]> donorFood = new HashMap<>(); // open batches, servings available
        for (FoodBatch b : open) {
            batchById.put(b.getId(), b);
            int[] v = donorFood.computeIfAbsent(b.getDonorId(), k -> new int[2]);
            v[0]++;
            v[1] += b.getRemainingServings();
        }

        List<Map<String, Object>> donorNodes = new ArrayList<>();
        List<GeoPoint> points = new ArrayList<>();
        for (Donor d : donors.findActive()) {
            Map<String, Object> m = d.toPublicMap();
            int[] v = donorFood.getOrDefault(d.getId(), new int[2]);
            m.put("openBatches", v[0]);
            m.put("servingsAvailable", v[1]);
            donorNodes.add(m);
            points.add(d.getLocation());
        }

        Map<String, Integer> usedToday = capacity.usedToday();
        List<Map<String, Object>> recipientNodes = new ArrayList<>();
        for (Recipient r : recipients.findActive()) {
            Map<String, Object> m = r.toPublicMap();
            m.put("remainingToday", capacity.remainingToday(r, usedToday));
            recipientNodes.add(m);
            points.add(r.getLocation());
        }

        List<Dispatch> active = dispatches.findActive();
        Map<String, Integer> activeByVolunteer = new HashMap<>();
        for (Dispatch d : active) {
            if (d.getVolunteerId() != null) {
                activeByVolunteer.merge(d.getVolunteerId(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> volunteerNodes = new ArrayList<>();
        if (full) {
            for (Volunteer v : volunteers.findActive()) {
                Map<String, Object> m = v.toPublicMap();
                m.put("activePickups", activeByVolunteer.getOrDefault(v.getId(), 0));
                volunteerNodes.add(m);
            }
        }

        List<Map<String, Object>> food = new ArrayList<>();
        for (FoodBatch b : open) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("donorId", b.getDonorId());
            m.put("foodName", b.getFoodName());
            m.put("category", b.getCategory());
            m.put("vegetarian", b.isVegetarian());
            m.put("remainingServings", b.getRemainingServings());
            m.put("servings", b.getServings());
            m.put("status", b.getStatus());
            m.put("minutesLeft", Math.max(0, b.minutesRemaining(now)));
            m.put("freshness", ViewAssembler.round2(b.freshness(now)));
            food.add(m);
        }

        // routes need the donor of each batch; active dispatches can point at ALLOCATED batches already in `open`
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Dispatch d : active) {
            FoodBatch b = batchById.get(d.getBatchId());
            if (b == null) {
                continue;
            }
            routes.add(route(d, b, full));
        }

        List<Dispatch> delivered = dispatches.findByStatus(DispatchStatus.DELIVERED);
        Map<String, FoodBatch> deliveredBatches = batches.findByIds(delivered.stream().limit(RECENT_DELIVERIES)
                .map(Dispatch::getBatchId).toList());
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Dispatch d : delivered.stream().limit(RECENT_DELIVERIES).toList()) {
            FoodBatch b = deliveredBatches.get(d.getBatchId());
            if (b != null) {
                recent.add(route(d, b, full));
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generatedAt", now);
        m.put("center", centroid(points).toMap());
        m.put("donors", donorNodes);
        m.put("recipients", recipientNodes);
        m.put("volunteers", volunteerNodes);
        m.put("batches", food);
        m.put("routes", routes);
        m.put("recentDeliveries", recent);
        return m;
    }

    private static Map<String, Object> route(Dispatch d, FoodBatch b, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("batchId", b.getId());
        m.put("foodName", b.getFoodName());
        m.put("donorId", b.getDonorId());
        m.put("recipientId", d.getRecipientId());
        m.put("volunteerId", full ? d.getVolunteerId() : null);
        m.put("status", d.getStatus());
        m.put("servings", d.getServings());
        m.put("distanceKm", ViewAssembler.round2(d.getDistanceKm()));
        m.put("assignedAt", d.getAssignedAt());
        m.put("claimedAt", d.getClaimedAt());
        m.put("pickedUpAt", d.getPickedUpAt());
        m.put("deliveredAt", d.getDeliveredAt());
        return m;
    }

    private static GeoPoint centroid(List<GeoPoint> points) {
        if (points.isEmpty()) {
            return GeoPoint.CAMPUS;
        }
        double lat = 0;
        double lng = 0;
        for (GeoPoint p : points) {
            lat += p.lat();
            lng += p.lng();
        }
        return new GeoPoint(lat / points.size(), lng / points.size());
    }
}
