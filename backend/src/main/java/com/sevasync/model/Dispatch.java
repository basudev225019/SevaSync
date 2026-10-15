package com.sevasync.model;

import com.sevasync.contract.Entity;
import com.sevasync.exception.IllegalTransitionException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A matched hand-over of servings from one batch to one recipient, carried by one volunteer.
 *
 * <p>Public life-cycle methods ({@link #claim}, {@link #pickUp} ...) delegate to the current {@link DispatchState};
 * the package-private {@code record*}/{@code moveTo} mutators can only be reached by those state objects.
 */
public class Dispatch implements Entity {
    private final String id;
    private final String batchId;
    private final String recipientId;
    private final int servings;
    private DispatchStatus status;
    private final String strategy;
    private final double distanceKm;
    private final LocalDateTime assignedAt;
    private String volunteerId;
    private LocalDateTime claimedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmedAt;
    private int rating;
    private String feedback;
    private String note;

    @SuppressWarnings("java:S107") // persistence constructor mirrors the table row
    public Dispatch(String id, String batchId, String recipientId, int servings, DispatchStatus status,
                    String strategy, double distanceKm, LocalDateTime assignedAt, String volunteerId,
                    LocalDateTime claimedAt, LocalDateTime pickedUpAt, LocalDateTime deliveredAt,
                    LocalDateTime confirmedAt, int rating, String feedback, String note) {
        this.id = Objects.requireNonNull(id);
        this.batchId = Objects.requireNonNull(batchId);
        this.recipientId = Objects.requireNonNull(recipientId);
        if (servings <= 0) {
            throw new IllegalArgumentException("servings must be positive");
        }
        this.servings = servings;
        this.status = Objects.requireNonNull(status);
        this.strategy = strategy == null ? "" : strategy;
        this.distanceKm = distanceKm;
        this.assignedAt = Objects.requireNonNull(assignedAt);
        this.volunteerId = volunteerId;
        this.claimedAt = claimedAt;
        this.pickedUpAt = pickedUpAt;
        this.deliveredAt = deliveredAt;
        this.confirmedAt = confirmedAt;
        this.rating = rating;
        this.feedback = feedback == null ? "" : feedback;
        this.note = note == null ? "" : note;
    }

    /** Factory for a freshly matched dispatch waiting for a volunteer. */
    public static Dispatch assigned(String id, String batchId, String recipientId, int servings, String strategy,
                                    double distanceKm, LocalDateTime now) {
        return new Dispatch(id, batchId, recipientId, servings, DispatchStatus.ASSIGNED, strategy, distanceKm, now,
                null, null, null, null, null, 0, "", "");
    }

    // ------------------------------------------------------------------ life-cycle (delegates to the state)

    public DispatchState state() {
        return status.state();
    }

    public void claim(String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        state().claim(this, Objects.requireNonNull(volunteerId), now);
    }

    public void release(String volunteerId) throws IllegalTransitionException {
        state().release(this, volunteerId);
    }

    public void pickUp(String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        state().pickUp(this, volunteerId, now);
    }

    public void deliver(String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        state().deliver(this, volunteerId, now);
    }

    public void confirm(int rating, String feedback, LocalDateTime now) throws IllegalTransitionException {
        state().confirm(this, rating, feedback, now);
    }

    public void cancel(String reason) throws IllegalTransitionException {
        state().cancel(this, reason);
    }

    // ------------------------------------------------------------------ mutators reserved for DispatchState

    void moveTo(DispatchStatus next) {
        this.status = next;
    }

    void recordClaim(String volunteer, LocalDateTime when) {
        this.volunteerId = volunteer;
        this.claimedAt = when;
    }

    void recordPickup(LocalDateTime when) {
        this.pickedUpAt = when;
    }

    void recordDelivery(LocalDateTime when) {
        this.deliveredAt = when;
    }

    void recordConfirmation(int stars, String text, LocalDateTime when) {
        this.rating = stars;
        this.feedback = text == null ? "" : text;
        this.confirmedAt = when;
    }

    void recordNote(String text) {
        this.note = text == null ? "" : text;
    }

    // ------------------------------------------------------------------ accessors

    public String getId() { return id; }
    public String getBatchId() { return batchId; }
    public String getRecipientId() { return recipientId; }
    public int getServings() { return servings; }
    public DispatchStatus getStatus() { return status; }
    public String getStrategy() { return strategy; }
    public double getDistanceKm() { return distanceKm; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public String getVolunteerId() { return volunteerId; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public LocalDateTime getPickedUpAt() { return pickedUpAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public int getRating() { return rating; }
    public String getFeedback() { return feedback; }
    public String getNote() { return note; }

    public boolean isActive() {
        return status.isActive();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("batchId", batchId);
        m.put("recipientId", recipientId);
        m.put("servings", servings);
        m.put("status", status);
        m.put("strategy", strategy);
        m.put("distanceKm", Math.round(distanceKm * 100) / 100.0);
        m.put("assignedAt", assignedAt);
        m.put("volunteerId", volunteerId);
        m.put("claimedAt", claimedAt);
        m.put("pickedUpAt", pickedUpAt);
        m.put("deliveredAt", deliveredAt);
        m.put("confirmedAt", confirmedAt);
        m.put("rating", rating == 0 ? null : rating);
        m.put("feedback", feedback);
        m.put("note", note);
        m.put("allowedActions", state().allowedActions());
        return m;
    }

    @Override
    public String toString() {
        return id + " " + batchId + "->" + recipientId + " x" + servings + " " + status;
    }
}
