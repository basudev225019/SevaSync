package com.sevasync.model;

import com.sevasync.contract.Entity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A planned or completed hand-over of servings from one batch to one recipient. */
public class Dispatch implements Entity {
    private static final long serialVersionUID = 1L;

    public static final String[] CSV_HEADER = {"id", "batchId", "recipientId", "servings", "status",
            "assignedAt", "deliveredAt", "volunteer", "note"};

    private final String id;
    private final String batchId;
    private final String recipientId;
    private final int servings;
    private DispatchStatus status;
    private final LocalDateTime assignedAt;
    private LocalDateTime deliveredAt;
    private String volunteer;
    private String note;

    public Dispatch(String id, String batchId, String recipientId, int servings, DispatchStatus status,
                    LocalDateTime assignedAt, LocalDateTime deliveredAt, String volunteer, String note) {
        this.id = Objects.requireNonNull(id);
        this.batchId = Objects.requireNonNull(batchId);
        this.recipientId = Objects.requireNonNull(recipientId);
        if (servings <= 0) {
            throw new IllegalArgumentException("servings must be positive");
        }
        this.servings = servings;
        this.status = Objects.requireNonNull(status);
        this.assignedAt = Objects.requireNonNull(assignedAt);
        this.deliveredAt = deliveredAt;
        this.volunteer = volunteer == null ? "" : volunteer;
        this.note = note == null ? "" : note;
    }

    public static Dispatch assigned(String id, String batchId, String recipientId, int servings, LocalDateTime now) {
        return new Dispatch(id, batchId, recipientId, servings, DispatchStatus.ASSIGNED, now, null, "", "");
    }

    public String getId() { return id; }
    public String getBatchId() { return batchId; }
    public String getRecipientId() { return recipientId; }
    public int getServings() { return servings; }
    public DispatchStatus getStatus() { return status; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public String getVolunteer() { return volunteer; }
    public String getNote() { return note; }

    public void markDelivered(LocalDateTime when, String volunteerName) {
        this.status = DispatchStatus.DELIVERED;
        this.deliveredAt = Objects.requireNonNull(when);
        this.volunteer = volunteerName == null ? "" : volunteerName;
    }

    public void cancel(String reason) {
        this.status = DispatchStatus.CANCELLED;
        this.note = reason == null ? "" : reason;
    }

    @Override
    public String[] toCsvRow() {
        return new String[]{id, batchId, recipientId, String.valueOf(servings), status.name(),
                assignedAt.toString(), deliveredAt == null ? "" : deliveredAt.toString(), volunteer, note};
    }

    public static Dispatch fromCsv(String[] f) {
        return new Dispatch(f[0], f[1], f[2], Integer.parseInt(f[3]), DispatchStatus.valueOf(f[4]),
                LocalDateTime.parse(f[5]), f[6].isEmpty() ? null : LocalDateTime.parse(f[6]), f[7], f[8]);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("batchId", batchId);
        m.put("recipientId", recipientId);
        m.put("servings", servings);
        m.put("status", status);
        m.put("assignedAt", assignedAt);
        m.put("deliveredAt", deliveredAt);
        m.put("volunteer", volunteer);
        m.put("note", note);
        return m;
    }

    @Override
    public String toString() {
        return id + " " + batchId + "->" + recipientId + " x" + servings + " " + status;
    }
}
