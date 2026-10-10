package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;

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

public class SqliteRecipientRepository implements Repository<Recipient> {
    private final DatabaseManager db;
    private final String idPrefix = "REC";

    public SqliteRecipientRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public synchronized Optional<Recipient> findById(String id) {
        String sql = "SELECT id, name, phone, email, address, recipient_type, need_level, daily_capacity, created_at FROM recipients WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new StorageException("Failed to query recipient by id " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<Recipient> findAll() {
        String sql = "SELECT id, name, phone, email, address, recipient_type, need_level, daily_capacity, created_at FROM recipients ORDER BY created_at ASC";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Recipient> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Failed to query all recipients: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Recipient> findWhere(Predicate<Recipient> filter) {
        List<Recipient> out = new ArrayList<>();
        for (Recipient r : findAll()) {
            if (filter.test(r)) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public synchronized int count() {
        String sql = "SELECT COUNT(*) FROM recipients";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to count recipients: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(Recipient recipient) {
        String sql = """
            INSERT INTO recipients (id, name, phone, email, address, recipient_type, need_level, daily_capacity, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                phone = excluded.phone,
                email = excluded.email,
                address = excluded.address,
                recipient_type = excluded.recipient_type,
                need_level = excluded.need_level,
                daily_capacity = excluded.daily_capacity;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRecipient(ps, recipient);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save recipient " + recipient.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveAll(Collection<Recipient> recipients) {
        String sql = """
            INSERT INTO recipients (id, name, phone, email, address, recipient_type, need_level, daily_capacity, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                phone = excluded.phone,
                email = excluded.email,
                address = excluded.address,
                recipient_type = excluded.recipient_type,
                need_level = excluded.need_level,
                daily_capacity = excluded.daily_capacity;
        """;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Recipient r : recipients) {
                    bindRecipient(ps, r);
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
            throw new StorageException("Failed to bulk save recipients: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void replaceAll(Collection<Recipient> recipients) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("DELETE FROM recipients");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new StorageException("Failed to clear recipients table: " + e.getMessage(), e);
        }
        saveAll(recipients);
    }

    @Override
    public synchronized String nextId() {
        return nextIds(1).get(0);
    }

    @Override
    public synchronized List<String> nextIds(int count) {
        int max = 0;
        String sql = "SELECT id FROM recipients";
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
            throw new StorageException("Failed to determine next recipient id: " + e.getMessage(), e);
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("%s-%04d", idPrefix, max + i));
        }
        return ids;
    }

    private void bindRecipient(PreparedStatement ps, Recipient r) throws SQLException {
        ps.setString(1, r.getId());
        ps.setString(2, r.getName());
        ps.setString(3, r.getPhone());
        ps.setString(4, r.getEmail());
        ps.setString(5, r.getAddress());
        ps.setString(6, r.getType().name());
        ps.setString(7, r.getNeedLevel().name());
        ps.setInt(8, r.getDailyCapacity());
        ps.setString(9, r.getCreatedAt().toString());
    }

    private Recipient mapRow(ResultSet rs) throws SQLException {
        return new Recipient(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                RecipientType.valueOf(rs.getString("recipient_type")),
                NeedLevel.valueOf(rs.getString("need_level")),
                rs.getInt("daily_capacity"),
                LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}
