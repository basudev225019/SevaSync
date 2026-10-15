package com.sevasync.service;

import com.sevasync.database.DatabaseManager;
import com.sevasync.event.Audience;
import com.sevasync.event.EventBus;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.matching.Allocation;
import com.sevasync.matching.MatchContext;
import com.sevasync.matching.MatchingStrategy;
import com.sevasync.matching.RecipientSlot;
import com.sevasync.matching.StrategyRegistry;
import com.sevasync.model.Dispatch;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.Permission;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.VehicleType;
import com.sevasync.security.Guard;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.SettingsRepository;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.storage.SqliteDonorRepository;
import com.sevasync.storage.SqliteFoodBatchRepository;
import com.sevasync.storage.SqliteRecipientRepository;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the active {@link MatchingStrategy} over today's open food and recipients (workflow step 2).
 * {@link #preview} is a dry run; {@link #run} persists the dispatches, batch allocations and audit rows in one
 * transaction. With auto-matching on, it runs by itself whenever food is logged or a match is cancelled.
 */
public class MatchingService {
    private static final Logger LOG = LoggerFactory.getLogger(MatchingService.class);

    /** Outcome of one run or preview. {@code items} are enriched dispatch views. */
    public record MatchResult(boolean committed, String strategy, List<Map<String, Object>> items,
                              int allocatedServings, int unallocatedServings, int batchesConsidered,
                              int recipientsAvailable, double averageDistanceKm) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("committed", committed);
            m.put("strategy", strategy);
            m.put("allocatedServings", allocatedServings);
            m.put("unallocatedServings", unallocatedServings);
            m.put("batchesConsidered", batchesConsidered);
            m.put("recipientsAvailable", recipientsAvailable);
            m.put("averageDistanceKm", Math.round(averageDistanceKm * 100) / 100.0);
            m.put("dispatches", items);
            return m;
        }
    }

    private record Plan(List<Allocation> allocations, Map<String, FoodBatch> batchesById,
                        Map<String, Recipient> recipientsById, Map<String, Donor> donorsById, int offered,
                        int batchesConsidered, int recipientsAvailable) {
    }

    private final DatabaseManager db;
    private final SqliteFoodBatchRepository batches;
    private final SqliteDispatchRepository dispatches;
    private final SqliteRecipientRepository recipients;
    private final SqliteDonorRepository donors;
    private final RecipientService capacity;
    private final DispatchService dispatchService;
    private final StrategyRegistry strategies;
    private final SettingsRepository settings;
    private final ViewAssembler views;
    private final AuditLogger audit;
    private final EventBus events;
    private final Clock clock;

    public MatchingService(DatabaseManager db, SqliteFoodBatchRepository batches, SqliteDispatchRepository dispatches,
                           SqliteRecipientRepository recipients, SqliteDonorRepository donors,
                           RecipientService capacity, DispatchService dispatchService, StrategyRegistry strategies,
                           SettingsRepository settings, ViewAssembler views, AuditLogger audit, EventBus events,
                           Clock clock) {
        this.db = db;
        this.batches = batches;
        this.dispatches = dispatches;
        this.recipients = recipients;
        this.donors = donors;
        this.capacity = capacity;
        this.dispatchService = dispatchService;
        this.strategies = strategies;
        this.settings = settings;
        this.views = views;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ settings

    public MatchingStrategy activeStrategy() {
        String id = settings.get(SettingsRepository.MATCHING_STRATEGY, StrategyRegistry.DEFAULT);
        try {
            return strategies.get(id);
        } catch (InvalidInputException e) {
            return strategies.all().get(0);
        }
    }

    public boolean isAutoMatch() {
        return settings.getBoolean(SettingsRepository.AUTO_MATCH, true);
    }

    public void configure(User actor, String strategyId, Boolean autoMatch) throws SevaSyncException {
        Guard.require(actor, Permission.MANAGE_SETTINGS);
        MatchingStrategy s = strategyId == null ? null : strategies.get(strategyId);
        db.runInTransaction(() -> {
            if (s != null) {
                settings.put(SettingsRepository.MATCHING_STRATEGY, s.id());
            }
            if (autoMatch != null) {
                settings.put(SettingsRepository.AUTO_MATCH, autoMatch.toString());
            }
            audit.log(actor.getId(), "SETTINGS_CHANGED", "System", "matching",
                    "strategy=" + activeStrategy().id() + ", auto=" + isAutoMatch());
            events.publish("SETTINGS_CHANGED", "System", "matching", "Matching now uses "
                    + activeStrategy().displayName(), Map.of("strategy", activeStrategy().id()), Audience.admins());
        });
    }

    public Map<String, Object> describeSettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeStrategy", activeStrategy().id());
        m.put("autoMatch", isAutoMatch());
        m.put("strategies", strategies.describe());
        return m;
    }

    // ------------------------------------------------------------------ running

    public MatchResult preview(User actor, String strategyId) throws SevaSyncException {
        Guard.require(actor, Permission.RUN_MATCHING);
        MatchingStrategy strategy = strategyId == null ? activeStrategy() : strategies.get(strategyId);
        return db.inTransaction(() -> {
            LocalDateTime now = TimeUtil.now(clock);
            Plan plan = plan(strategy, now);
            List<Dispatch> preview = new ArrayList<>();
            int i = 1;
            for (Allocation a : plan.allocations()) {
                preview.add(Dispatch.assigned("PREVIEW-" + i++, a.batchId(), a.recipientId(), a.servings(),
                        strategy.id(), a.distanceKm(), now));
            }
            return result(false, strategy, preview, plan);
        });
    }

    public MatchResult run(User actor, String strategyId) throws SevaSyncException {
        Guard.require(actor, Permission.RUN_MATCHING);
        MatchingStrategy strategy = strategyId == null ? activeStrategy() : strategies.get(strategyId);
        return execute(strategy, actor.getId());
    }

    /** Called after food is logged or released; does nothing when auto-matching is switched off. */
    public void autoMatch() {
        if (!isAutoMatch()) {
            return;
        }
        try {
            MatchResult r = execute(activeStrategy(), AuditLogger.SYSTEM);
            if (r.allocatedServings() > 0) {
                LOG.info("Auto-match created {} dispatches ({} servings)", r.items().size(), r.allocatedServings());
            }
        } catch (RuntimeException e) {
            LOG.warn("Auto-match failed: {}", e.toString());
        }
    }

    private MatchResult execute(MatchingStrategy strategy, String actorId) {
        dispatchService.expireOverdue();
        return db.inTransaction(() -> {
            LocalDateTime now = TimeUtil.now(clock);
            Plan plan = plan(strategy, now);
            List<Dispatch> created = new ArrayList<>();
            for (Allocation a : plan.allocations()) {
                FoodBatch b = plan.batchesById().get(a.batchId());
                b.allocate(a.servings()); // throws if a strategy ever over-allocates, rolling everything back
                Dispatch d = Dispatch.assigned(dispatches.nextId(), a.batchId(), a.recipientId(), a.servings(),
                        strategy.id(), a.distanceKm(), now);
                created.add(d);
            }
            if (!created.isEmpty()) {
                dispatches.saveAll(created);
                Set<FoodBatch> touched = new LinkedHashSet<>();
                for (Allocation a : plan.allocations()) {
                    touched.add(plan.batchesById().get(a.batchId()));
                }
                batches.saveAll(touched);
                for (Dispatch d : created) {
                    FoodBatch b = plan.batchesById().get(d.getBatchId());
                    audit.log(actorId, "DISPATCH_ASSIGNED", "Dispatch", d.getId(), d.getServings() + " servings of "
                            + b.getFoodName() + " -> " + d.getRecipientId() + " (" + strategy.id() + ")");
                    events.publish("DISPATCH_ASSIGNED", "Dispatch", d.getId(), "New pickup: " + d.getServings()
                                    + " servings of " + b.getFoodName(),
                            Map.of("status", d.getStatus(), "batchId", b.getId(), "servings", d.getServings()),
                            Audience.roles(Role.VOLUNTEER).and(Audience.users(d.getRecipientId(), b.getDonorId())));
                }
            }
            MatchResult result = result(true, strategy, created, plan);
            audit.log(actorId, "MATCHING_RUN", "System", strategy.id(), created.size() + " dispatches, "
                    + result.allocatedServings() + " servings matched, " + result.unallocatedServings() + " waiting");
            if (!created.isEmpty()) {
                events.publish("MATCHING_RUN", "System", strategy.id(), "Matched " + result.allocatedServings()
                        + " servings to " + created.size() + " pickups", Map.of("dispatches", created.size()),
                        Audience.admins());
            }
            return result;
        });
    }

    private Plan plan(MatchingStrategy strategy, LocalDateTime now) {
        List<FoodBatch> open = batches.findMatchable(now.plusMinutes(SafetyValidator.MIN_DISPATCH_MINUTES));
        Map<String, Donor> donorsById = new HashMap<>();
        Map<String, GeoPoint> donorLocations = new HashMap<>();
        for (Donor d : donors.findAll()) {
            donorsById.put(d.getId(), d);
            donorLocations.put(d.getId(), d.getLocation());
        }
        Map<String, Integer> today = capacity.usedToday();
        Map<String, Integer> week = capacity.usedLastWeek();
        List<RecipientSlot> slots = new ArrayList<>();
        Map<String, Recipient> recipientsById = new HashMap<>();
        for (Recipient r : recipients.findActive()) {
            recipientsById.put(r.getId(), r);
            int free = capacity.remainingToday(r, today);
            if (free > 0) {
                slots.add(new RecipientSlot(r, free, week.getOrDefault(r.getId(), 0)));
            }
        }
        Map<String, FoodBatch> batchesById = new LinkedHashMap<>();
        int offered = 0;
        for (FoodBatch b : open) {
            batchesById.put(b.getId(), b);
            offered += b.getRemainingServings();
        }
        MatchContext ctx = new MatchContext(now, donorLocations, SafetyValidator.MIN_DISPATCH_MINUTES,
                VehicleType.BICYCLE);
        List<Allocation> allocations = strategy.allocate(open, slots, ctx);
        return new Plan(allocations, batchesById, recipientsById, donorsById, offered, open.size(), slots.size());
    }

    private MatchResult result(boolean committed, MatchingStrategy strategy, List<Dispatch> list, Plan plan) {
        int allocated = list.stream().mapToInt(Dispatch::getServings).sum();
        double avgKm = list.isEmpty() ? 0 : list.stream().mapToDouble(Dispatch::getDistanceKm).average().orElse(0);
        Map<String, Map<String, Object>> batchViews = new HashMap<>();
        for (Map<String, Object> v : views.batches(plan.batchesById().values())) {
            batchViews.put((String) v.get("id"), v);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Dispatch d : list) {
            Map<String, Object> m = d.toMap();
            FoodBatch b = plan.batchesById().get(d.getBatchId());
            Recipient r = plan.recipientsById().get(d.getRecipientId());
            Donor dn = b == null ? null : plan.donorsById().get(b.getDonorId());
            m.put("batch", batchViews.get(d.getBatchId()));
            m.put("recipient", r == null ? null : r.toPublicMap());
            m.put("donor", dn == null ? null : dn.toPublicMap());
            items.add(m);
        }
        return new MatchResult(committed, strategy.id(), items, allocated, Math.max(0, plan.offered() - allocated),
                plan.batchesConsidered(), plan.recipientsAvailable(), avgKm);
    }
}
