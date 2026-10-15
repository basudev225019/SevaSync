package com.sevasync.database;

import com.sevasync.exception.StorageException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the single SQLite connection and serialises all access to it.
 *
 * <p>SevaSync runs as one JVM, so one connection guarded by a fair {@link ReentrantLock} gives serialisable
 * transactions with no connection-pool complexity. {@link #inTransaction} is re-entrant: a service that calls
 * another service joins the outer transaction instead of committing half-way. Work registered with
 * {@link #afterCommit(Runnable)} (e.g. live events) only runs once the data is really stored.
 */
public final class DatabaseManager implements AutoCloseable {
    /** Unit of work that may fail with a checked exception of type {@code E}. */
    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    /** Maps the current row of a result set to an object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Storage format for every timestamp column. */
    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String jdbcUrl;
    private final Connection connection;
    private final ReentrantLock lock = new ReentrantLock(true);
    private int txDepth;
    private final List<Runnable> afterCommit = new ArrayList<>();

    private DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA busy_timeout = 5000");
                if (!jdbcUrl.contains(":memory:")) {
                    s.execute("PRAGMA journal_mode = WAL");
                    s.execute("PRAGMA synchronous = NORMAL");
                }
            }
        } catch (ClassNotFoundException e) {
            throw new StorageException("SQLite JDBC driver not found on classpath", e);
        } catch (SQLException e) {
            throw new StorageException("Cannot open database " + jdbcUrl + ": " + e.getMessage(), e);
        }
    }

    public static DatabaseManager forFile(Path dbPath) {
        try {
            if (dbPath.toAbsolutePath().getParent() != null) {
                Files.createDirectories(dbPath.toAbsolutePath().getParent());
            }
        } catch (Exception e) {
            throw new StorageException("Cannot create directory for database: " + e.getMessage(), e);
        }
        return new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public static DatabaseManager inMemory() {
        return new DatabaseManager("jdbc:sqlite::memory:");
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    // ------------------------------------------------------------------ transactions

    /**
     * Runs {@code work} atomically: everything inside commits together or rolls back together.
     * Nested calls join the outermost transaction.
     */
    public <T, E extends Exception> T inTransaction(Work<T, E> work) throws E {
        lock.lock();
        try {
            boolean outermost = txDepth == 0;
            if (outermost) {
                setAutoCommit(false);
            }
            txDepth++;
            T result;
            try {
                result = work.run();
            } catch (Exception | Error e) {
                txDepth--;
                if (outermost) {
                    rollbackQuietly();
                    afterCommit.clear();
                }
                throw e;
            }
            txDepth--;
            if (outermost) {
                commit();
                runAfterCommitHooks();
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** Convenience for work that returns nothing and throws no checked exception. */
    public void runInTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** Defers {@code task} until the surrounding transaction commits (runs at once outside a transaction). */
    public void afterCommit(Runnable task) {
        lock.lock();
        try {
            if (txDepth == 0) {
                task.run();
            } else {
                afterCommit.add(task);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isInTransaction() {
        lock.lock();
        try {
            return txDepth > 0;
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ statements

    public int update(String sql, Object... params) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Update failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /** Executes the same statement for every parameter row, inside one transaction. */
    public void batch(String sql, List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        runInTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Object[] row : rows) {
                    bind(ps, row);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new StorageException("Batch update failed: " + e.getMessage(), e);
            }
        });
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new StorageException("Query failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long queryLong(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    /** Runs a multi-statement script (used by the migrator). */
    public void executeScript(String script) {
        lock.lock();
        try (Statement s = connection.createStatement()) {
            for (String statement : script.split(";")) {
                String sql = stripComments(statement).trim();
                if (!sql.isEmpty()) {
                    s.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Script failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /** Consistent online snapshot of the whole database into {@code target} (SQLite {@code VACUUM INTO}). */
    public void snapshotTo(Path target) {
        lock.lock();
        try (Statement s = connection.createStatement()) {
            Files.deleteIfExists(target);
            s.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        } catch (Exception e) {
            throw new StorageException("Snapshot failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ internals

    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.strip();
            if (!t.startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Enum<?> e) {
                ps.setString(i + 1, e.name());
            } else if (p instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else if (p instanceof LocalDateTime t) {
                // fixed width, so ISO strings sort and compare correctly inside SQL
                ps.setString(i + 1, t.format(TIMESTAMP));
            } else if (p instanceof java.time.temporal.TemporalAccessor) {
                ps.setString(i + 1, p.toString());
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    private void setAutoCommit(boolean on) {
        try {
            connection.setAutoCommit(on);
        } catch (SQLException e) {
            throw new StorageException("Cannot change auto-commit: " + e.getMessage(), e);
        }
    }

    private void commit() {
        try {
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            afterCommit.clear();
            throw new StorageException("Commit failed: " + e.getMessage(), e);
        } finally {
            setAutoCommit(true);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // nothing left to undo
        } finally {
            setAutoCommit(true);
        }
    }

    private void runAfterCommitHooks() {
        List<Runnable> hooks = new ArrayList<>(afterCommit);
        afterCommit.clear();
        for (Runnable r : hooks) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // a failing listener must never undo or block a committed business change
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // closing anyway
        } finally {
            lock.unlock();
        }
    }
}
