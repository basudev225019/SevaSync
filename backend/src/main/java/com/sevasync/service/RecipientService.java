package com.sevasync.service;

import com.sevasync.model.Recipient;
import com.sevasync.storage.SqliteDispatchRepository;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/** Daily-capacity bookkeeping: how much more food each recipient can take today, and what it got this week. */
public class RecipientService {
    private final SqliteDispatchRepository dispatches;
    private final Clock clock;

    public RecipientService(SqliteDispatchRepository dispatches, Clock clock) {
        this.dispatches = dispatches;
        this.clock = clock;
    }

    /** Servings matched to each recipient today (anything not cancelled counts against the day's capacity). */
    public Map<String, Integer> usedToday() {
        return dispatches.servingsByRecipientSince(startOfToday());
    }

    /** Servings matched to each recipient over the last seven days (fairness input for matching). */
    public Map<String, Integer> usedLastWeek() {
        return dispatches.servingsByRecipientSince(TimeUtil.now(clock).minusDays(7));
    }

    public int remainingToday(Recipient r) {
        return remainingToday(r, usedToday());
    }

    public int remainingToday(Recipient r, Map<String, Integer> usedToday) {
        return Math.max(0, r.getDailyCapacity() - usedToday.getOrDefault(r.getId(), 0));
    }

    private LocalDateTime startOfToday() {
        return TimeUtil.now(clock).toLocalDate().atStartOfDay();
    }
}
