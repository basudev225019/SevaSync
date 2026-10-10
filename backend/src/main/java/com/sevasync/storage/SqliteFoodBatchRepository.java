package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class SqliteFoodBatchRepository implements Repository<FoodBatch> {
    private final DatabaseManager db;
    private final String idPrefix = "BAT";

    public SqliteFoodBatchRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public synchronized Optional<FoodBatch> findById(String id) {
        String sql = """
            SELECT id, donor_id, food_name, category, vegetarian, servings, remaining_servings,
                   prepared_at, shelf_life_minutes, status, created_at
            FROM food_batches WHERE id = ?
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new StorageException("Failed to query food batch by id " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<FoodBatch> findAll() {
        String sql = """
            SELECT id, donor_id, food_name, category, vegetarian, servings, remaining_servings,
                   prepared_at, shelf_life_minutes, status, created_at
            FROM food_batches ORDER BY created_at ASC
        """;
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<FoodBatch> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Failed to query all food batches: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FoodBatch> findWhere(Predicate<FoodBatch> filter) {
        List<FoodBatch> out = new ArrayList<>();
        for (FoodBatch b : findAll()) {
            if (filter.test(b)) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    public synchronized int count() {
        String sql = "SELECT COUNT(*) FROM food_batches";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to count food batches: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(FoodBatch batch) {
        String sql = """
            INSERT INTO food_batches (id, donor_id, food_name, category, vegetarian, servings, remaining_servings,
                                      prepared_at, shelf_life_minutes, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                donor_id = excluded.donor_id,
                food_name = excluded.food_name,
                category = excluded.category,
                vegetarian = excluded.vegetarian,
                servings = excluded.servings,
                remaining_servings = excluded.remaining_servings,
                prepared_at = excluded.prepared_at,
                shelf_life_minutes = excluded.shelf_life_minutes,
                status = excluded.status;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindBatch(ps, batch);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save food batch " + batch.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveAll(Collection<FoodBatch> batches) {
        String sql = """
            INSERT INTO food_batches (id, donor_id, food_name, category, vegetarian, servings, remaining_servings,
                                      prepared_at, shelf_life_minutes, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                donor_id = excluded.donor_id,
                food_name = excluded.food_name,
                category = excluded.category,
                vegetarian = excluded.vegetarian,
                servings = excluded.servings,
                remaining_servings = excluded.remaining_servings,
                prepared_at = excluded.prepared_at,
                shelf_life_minutes = excluded.shelf_life_minutes,
                status = excluded.status;
        """;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (FoodBatch b : batches) {
                    bindBatch(ps, b);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to bulk save food batches: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void replaceAll(Collection<FoodBatch> batches) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("DELETE FROM food_batches");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new StorageException("Failed to clear food batches table: " + e.getMessage(), e);
        }
        saveAll(batches);
    }

    @Override
    public synchronized String nextId() {
        return nextIds(1).get(0);
    }

    @Override
    public synchronized List<String> nextIds(int count) {
        int max = 0;
        String sql = "SELECT id FROM food_batches";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                int dash = id.lastIndexOf('-');
                if (dash >= 0) {
                    try {
                        max = Math.max(max, Integer.parseInt(id.substring(dash + 1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to determine next food batch id: " + e.getMessage(), e);
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("%s-%04d", idPrefix, max + i));
        }
        return ids;
    }

    private void bindBatch(PreparedStatement ps, FoodBatch b) throws SQLException {
        ps.setString(1, b.getId());
        ps.setString(2, b.getDonorId());
        ps.setString(3, b.getFoodName());
        ps.setString(4, b.getCategory().name());
        ps.setInt(5, b.isVegetarian() ? 1 : 0);
        ps.setInt(6, b.getServings());
        ps.setInt(7, b.getRemainingServings());
        ps.setString(8, b.getPreparedAt().toString());
        ps.setInt(9, b.getShelfLifeMinutes());
        ps.setString(10, b.getStatus().name());
        ps.setString(11, b.getCreatedAt().toString());
    }

    private FoodBatch mapRow(ResultSet rs) throws SQLException {
        return new FoodBatch(
                rs.getString("id"),
                rs.getString("donor_id"),
                rs.getString("food_name"),
                FoodCategory.valueOf(rs.getString("category")),
                rs.getInt("vegetarian") == 1,
                rs.getInt("servings"),
                rs.getInt("remaining_servings"),
                LocalDateTime.parse(rs.getString("prepared_at")),
                rs.getInt("shelf_life_minutes"),
                BatchStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}
