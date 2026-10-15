package com.sevasync.storage;

import com.sevasync.contract.Entity;
import com.sevasync.database.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Template Method base for SQLite repositories. The generic CRUD algorithm lives here once; a concrete repository
 * only declares its table, its columns and how one row maps to an object ({@link #map}) and back ({@link #values}).
 *
 * @param <T> entity type
 */
public abstract class AbstractSqliteRepository<T extends Entity> implements Repository<T> {
    protected final DatabaseManager db;
    private final String table;
    private final List<String> columns;
    private final String selectSql;
    private final String upsertSql;

    /** @param columns column names in {@link #values} order; the first one must be {@code id} */
    protected AbstractSqliteRepository(DatabaseManager db, String table, String... columns) {
        if (columns.length == 0 || !columns[0].equals("id")) {
            throw new IllegalArgumentException("first column must be id");
        }
        this.db = db;
        this.table = table;
        this.columns = List.of(columns);
        this.selectSql = "SELECT " + String.join(", ", columns) + " FROM " + table;
        String placeholders = Arrays.stream(columns).map(c -> "?").collect(Collectors.joining(", "));
        String updates = Arrays.stream(columns).skip(1).map(c -> c + " = excluded." + c)
                .collect(Collectors.joining(", "));
        this.upsertSql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders
                + ") ON CONFLICT(id) DO UPDATE SET " + updates;
    }

    /** Builds an entity from the current row (hook). */
    protected abstract T map(ResultSet rs) throws SQLException;

    /** Column values of {@code entity}, in constructor column order (hook). */
    protected abstract Object[] values(T entity);

    /** Default ordering of {@link #findAll()}. */
    protected String orderBy() {
        return "id";
    }

    @Override
    public Optional<T> findById(String id) {
        return db.queryOne(selectSql + " WHERE id = ?", this::map, id);
    }

    @Override
    public List<T> findAll() {
        return db.query(selectSql + " ORDER BY " + orderBy(), this::map);
    }

    /** Rows matching an SQL condition, e.g. {@code where("status = ?", "AVAILABLE")}. */
    protected List<T> where(String condition, Object... params) {
        return db.query(selectSql + " WHERE " + condition + " ORDER BY " + orderBy(), this::map, params);
    }

    /** Like {@link #where(String, Object...)} with an explicit ORDER BY (kept a separate name to avoid overload mix-ups). */
    protected List<T> whereOrdered(String condition, String order, Object... params) {
        return db.query(selectSql + " WHERE " + condition + " ORDER BY " + order, this::map, params);
    }

    @Override
    public long count() {
        return db.queryLong("SELECT COUNT(*) FROM " + table);
    }

    @Override
    public void save(T entity) {
        db.update(upsertSql, checked(entity));
    }

    @Override
    public void saveAll(Collection<? extends T> entities) {
        List<Object[]> rows = new ArrayList<>(entities.size());
        for (T e : entities) {
            rows.add(checked(e));
        }
        db.batch(upsertSql, rows);
    }

    /** Allocates the next human-readable id with the given prefix, e.g. {@code BAT-0007}. */
    protected String generateId(String prefix) {
        return db.inTransaction(() -> {
            db.update("INSERT INTO sequences (name, value) VALUES (?, 1) "
                    + "ON CONFLICT(name) DO UPDATE SET value = value + 1", prefix);
            long n = db.queryLong("SELECT value FROM sequences WHERE name = ?", prefix);
            return String.format("%s-%04d", prefix, n);
        });
    }

    private Object[] checked(T entity) {
        Object[] v = values(entity);
        if (v.length != columns.size()) {
            throw new IllegalStateException(table + ": " + v.length + " values for " + columns.size() + " columns");
        }
        return v;
    }

    // ------------------------------------------------------------------ row helpers

    protected static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        String s = rs.getString(column);
        return s == null || s.isEmpty() ? null : LocalDateTime.parse(s);
    }

    protected static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    protected static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
