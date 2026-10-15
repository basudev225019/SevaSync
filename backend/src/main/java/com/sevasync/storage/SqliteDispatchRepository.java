package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteDispatchRepository extends AbstractSqliteRepository<Dispatch> {
    private static final String ACTIVE = "status IN ('ASSIGNED', 'CLAIMED', 'PICKED_UP')";

    public SqliteDispatchRepository(DatabaseManager db) {
        super(db, "dispatches", "id", "batch_id", "recipient_id", "volunteer_id", "servings", "status", "strategy",
                "distance_km", "assigned_at", "claimed_at", "picked_up_at", "delivered_at", "confirmed_at", "rating",
                "feedback", "note");
    }

    @Override
    protected String orderBy() {
        return "assigned_at DESC, id DESC";
    }

    @Override
    protected Dispatch map(ResultSet rs) throws SQLException {
        return new Dispatch(rs.getString("id"), rs.getString("batch_id"), rs.getString("recipient_id"),
                rs.getInt("servings"), DispatchStatus.valueOf(rs.getString("status")), rs.getString("strategy"),
                rs.getDouble("distance_km"), time(rs, "assigned_at"), rs.getString("volunteer_id"),
                time(rs, "claimed_at"), time(rs, "picked_up_at"), time(rs, "delivered_at"),
                time(rs, "confirmed_at"), rs.getInt("rating"), rs.getString("feedback"), rs.getString("note"));
    }

    @Override
    protected Object[] values(Dispatch d) {
        return new Object[]{d.getId(), d.getBatchId(), d.getRecipientId(), d.getVolunteerId(), d.getServings(),
                d.getStatus(), d.getStrategy(), d.getDistanceKm(), d.getAssignedAt(), d.getClaimedAt(),
                d.getPickedUpAt(), d.getDeliveredAt(), d.getConfirmedAt(), d.getRating(), d.getFeedback(),
                d.getNote()};
    }

    public String nextId() {
        return generateId("DSP");
    }

    public List<Dispatch> findByStatus(DispatchStatus status) {
        return where("status = ?", status);
    }

    public List<Dispatch> findByBatch(String batchId) {
        return where("batch_id = ?", batchId);
    }

    public List<Dispatch> findByRecipient(String recipientId) {
        return where("recipient_id = ?", recipientId);
    }

    public List<Dispatch> findByVolunteer(String volunteerId) {
        return where("volunteer_id = ?", volunteerId);
    }

    /** Dispatches for batches donated by {@code donorId}. */
    public List<Dispatch> findByDonor(String donorId) {
        return where("batch_id IN (SELECT id FROM food_batches WHERE donor_id = ?)", donorId);
    }

    public List<Dispatch> findActive() {
        return where(ACTIVE);
    }

    public List<Dispatch> findActiveByBatch(String batchId) {
        return where(ACTIVE + " AND batch_id = ?", batchId);
    }

    public long countActiveByVolunteer(String volunteerId) {
        return db.queryLong("SELECT COUNT(*) FROM dispatches WHERE " + ACTIVE + " AND volunteer_id = ?",
                volunteerId);
    }

    /** Servings matched to each recipient since {@code since} (cancelled dispatches do not count). */
    public Map<String, Integer> servingsByRecipientSince(LocalDateTime since) {
        Map<String, Integer> out = new HashMap<>();
        db.query("SELECT recipient_id, SUM(servings) FROM dispatches WHERE status <> 'CANCELLED' AND assigned_at >= ?"
                        + " GROUP BY recipient_id",
                rs -> out.put(rs.getString(1), rs.getInt(2)), since);
        return out;
    }
}
