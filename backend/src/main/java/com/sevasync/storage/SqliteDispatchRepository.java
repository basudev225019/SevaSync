package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;

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

public class SqliteDispatchRepository implements Repository<Dispatch> {
    private final DatabaseManager db;
    private final String idPrefix = "DSP";

    public SqliteDispatchRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public synchronized Optional<Dispatch> findById(String id) {
        String sql = """
            SELECT id, batch_id, recipient_id, servings, status, assigned_at, delivered_at, volunteer_name, note
            FROM dispatches WHERE id = ?
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
            throw new StorageException("Failed to query dispatch by id " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<Dispatch> findAll() {
        String sql = """
            SELECT id, batch_id, recipient_id, servings, status, assigned_at, delivered_at, volunteer_name, note
            FROM dispatches ORDER BY assigned_at ASC
        """;
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Dispatch> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Failed to query all dispatches: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Dispatch> findWhere(Predicate<Dispatch> filter) {
        List<Dispatch> out = new ArrayList<>();
        for (Dispatch d : findAll()) {
            if (filter.test(d)) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public synchronized int count() {
        String sql = "SELECT COUNT(*) FROM dispatches";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to count dispatches: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(Dispatch dispatch) {
        String sql = """
            INSERT INTO dispatches (id, batch_id, recipient_id, servings, status, assigned_at, delivered_at, volunteer_name, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                batch_id = excluded.batch_id,
                recipient_id = excluded.recipient_id,
                servings = excluded.servings,
                status = excluded.status,
                assigned_at = excluded.assigned_at,
                delivered_at = excluded.delivered_at,
                volunteer_name = excluded.volunteer_name,
                note = excluded.note;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDispatch(ps, dispatch);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save dispatch " + dispatch.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveAll(Collection<Dispatch> dispatches) {
        String sql = """
            INSERT INTO dispatches (id, batch_id, recipient_id, servings, status, assigned_at, delivered_at, volunteer_name, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                batch_id = excluded.batch_id,
                recipient_id = excluded.recipient_id,
                servings = excluded.servings,
                status = excluded.status,
                assigned_at = excluded.assigned_at,
                delivered_at = excluded.delivered_at,
                volunteer_name = excluded.volunteer_name,
                note = excluded.note;
        """;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Dispatch d : dispatches) {
                    bindDispatch(ps, d);
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
            throw new StorageException("Failed to bulk save dispatches: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void replaceAll(Collection<Dispatch> dispatches) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("DELETE FROM dispatches");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new StorageException("Failed to clear dispatches table: " + e.getMessage(), e);
        }
        saveAll(dispatches);
    }

    @Override
    public synchronized String nextId() {
        return nextIds(1).get(0);
    }

    @Override
    public synchronized List<String> nextIds(int count) {
        int max = 0;
        String sql = "SELECT id FROM dispatches";
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
            throw new StorageException("Failed to determine next dispatch id: " + e.getMessage(), e);
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("%s-%04d", idPrefix, max + i));
        }
        return ids;
    }

    private void bindDispatch(PreparedStatement ps, Dispatch d) throws SQLException {
        ps.setString(1, d.getId());
        ps.setString(2, d.getBatchId());
        ps.setString(3, d.getRecipientId());
        ps.setInt(4, d.getServings());
        ps.setString(5, d.getStatus().name());
        ps.setString(6, d.getAssignedAt().toString());
        ps.setString(7, d.getDeliveredAt() == null ? null : d.getDeliveredAt().toString());
        ps.setString(8, d.getVolunteer());
        ps.setString(9, d.getNote());
    }

    private Dispatch mapRow(ResultSet rs) throws SQLException {
        String delAtStr = rs.getString("delivered_at");
        LocalDateTime delAt = (delAtStr == null || delAtStr.isEmpty()) ? null : LocalDateTime.parse(delAtStr);
        return new Dispatch(
                rs.getString("id"),
                rs.getString("batch_id"),
                rs.getString("recipient_id"),
                rs.getInt("servings"),
                DispatchStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("assigned_at")),
                delAt,
                rs.getString("volunteer_name"),
                rs.getString("note")
        );
    }
}
