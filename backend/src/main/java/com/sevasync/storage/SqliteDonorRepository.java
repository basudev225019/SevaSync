package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.exception.StorageException;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;

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

public class SqliteDonorRepository implements Repository<Donor> {
    private final DatabaseManager db;
    private final String idPrefix = "DON";

    public SqliteDonorRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public synchronized Optional<Donor> findById(String id) {
        String sql = "SELECT id, name, phone, email, address, donor_type, created_at FROM donors WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new StorageException("Failed to query donor by id " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<Donor> findAll() {
        String sql = "SELECT id, name, phone, email, address, donor_type, created_at FROM donors ORDER BY created_at ASC";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<Donor> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new StorageException("Failed to query all donors: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Donor> findWhere(Predicate<Donor> filter) {
        List<Donor> out = new ArrayList<>();
        for (Donor d : findAll()) {
            if (filter.test(d)) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public synchronized int count() {
        String sql = "SELECT COUNT(*) FROM donors";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to count donors: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(Donor donor) {
        String sql = """
            INSERT INTO donors (id, name, phone, email, address, donor_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                phone = excluded.phone,
                email = excluded.email,
                address = excluded.address,
                donor_type = excluded.donor_type;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDonor(ps, donor);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save donor " + donor.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void saveAll(Collection<Donor> donors) {
        String sql = """
            INSERT INTO donors (id, name, phone, email, address, donor_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                phone = excluded.phone,
                email = excluded.email,
                address = excluded.address,
                donor_type = excluded.donor_type;
        """;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Donor d : donors) {
                    bindDonor(ps, d);
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
            throw new StorageException("Failed to bulk save donors: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void replaceAll(Collection<Donor> donors) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("DELETE FROM donors");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new StorageException("Failed to clear donors table: " + e.getMessage(), e);
        }
        saveAll(donors);
    }

    @Override
    public synchronized String nextId() {
        return nextIds(1).get(0);
    }

    @Override
    public synchronized List<String> nextIds(int count) {
        int max = 0;
        String sql = "SELECT id FROM donors";
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
            throw new StorageException("Failed to determine next donor id: " + e.getMessage(), e);
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("%s-%04d", idPrefix, max + i));
        }
        return ids;
    }

    private void bindDonor(PreparedStatement ps, Donor d) throws SQLException {
        ps.setString(1, d.getId());
        ps.setString(2, d.getName());
        ps.setString(3, d.getPhone());
        ps.setString(4, d.getEmail());
        ps.setString(5, d.getAddress());
        ps.setString(6, d.getType().name());
        ps.setString(7, d.getCreatedAt().toString());
    }

    private Donor mapRow(ResultSet rs) throws SQLException {
        return new Donor(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                DonorType.valueOf(rs.getString("donor_type")),
                LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}
