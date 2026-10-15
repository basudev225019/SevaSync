package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteFoodBatchRepository extends AbstractSqliteRepository<FoodBatch> {

    public SqliteFoodBatchRepository(DatabaseManager db) {
        super(db, "food_batches", "id", "donor_id", "food_name", "category", "vegetarian", "servings",
                "remaining_servings", "prepared_at", "shelf_life_minutes", "expires_at", "status", "notes",
                "created_at");
    }

    @Override
    protected String orderBy() {
        return "created_at DESC, id DESC";
    }

    @Override
    protected FoodBatch map(ResultSet rs) throws SQLException {
        return new FoodBatch(rs.getString("id"), rs.getString("donor_id"), rs.getString("food_name"),
                FoodCategory.valueOf(rs.getString("category")), rs.getInt("vegetarian") == 1, rs.getInt("servings"),
                rs.getInt("remaining_servings"), time(rs, "prepared_at"), rs.getInt("shelf_life_minutes"),
                BatchStatus.valueOf(rs.getString("status")), rs.getString("notes"), time(rs, "created_at"));
    }

    @Override
    protected Object[] values(FoodBatch b) {
        return new Object[]{b.getId(), b.getDonorId(), b.getFoodName(), b.getCategory(), b.isVegetarian(),
                b.getServings(), b.getRemainingServings(), b.getPreparedAt(), b.getShelfLifeMinutes(),
                b.getExpiresAt(), b.getStatus(), b.getNotes(), b.getCreatedAt()};
    }

    public String nextId() {
        return generateId("BAT");
    }

    public List<FoodBatch> findByDonor(String donorId) {
        return where("donor_id = ?", donorId);
    }

    public List<FoodBatch> findByStatus(BatchStatus status) {
        return where("status = ?", status);
    }

    /** AVAILABLE batches with servings left that do not expire before {@code notBefore}, soonest expiry first. */
    public List<FoodBatch> findMatchable(LocalDateTime notBefore) {
        return whereOrdered("status = 'AVAILABLE' AND remaining_servings > 0 AND expires_at > ?",
                "expires_at, id", notBefore);
    }

    /** Open (AVAILABLE or ALLOCATED) batches whose consumption window has closed. */
    public List<FoodBatch> findOverdue(LocalDateTime now) {
        return whereOrdered("status IN ('AVAILABLE', 'ALLOCATED') AND expires_at <= ?", "expires_at, id", now);
    }

    public List<FoodBatch> findOpen() {
        return whereOrdered("status IN ('AVAILABLE', 'ALLOCATED')", "expires_at, id");
    }

    public Map<String, FoodBatch> findByIds(Collection<String> ids) {
        Map<String, FoodBatch> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<String> list = List.copyOf(new java.util.HashSet<>(ids));
        String marks = String.join(", ", java.util.Collections.nCopies(list.size(), "?"));
        for (FoodBatch b : where("id IN (" + marks + ")", list.toArray())) {
            out.put(b.getId(), b);
        }
        return out;
    }

    /** Batches created since {@code since} (for the impact timeline). */
    public List<FoodBatch> findCreatedSince(LocalDateTime since) {
        return whereOrdered("created_at >= ?", "created_at", since);
    }
}
