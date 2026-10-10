package com.sevasync.storage;

import com.sevasync.contract.Entity;
import com.sevasync.exception.StorageException;
import com.sevasync.util.CsvUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Generic repository persisting entities of one type into a CSV file.
 *
 * <p>Rows are kept in memory (a HashMap-backed LinkedHashMap) and every call that changes data rewrites
 * the file atomically. Callers always receive <em>fresh copies</em> built from the stored row, so mutating a
 * returned object never changes stored data until {@link #save(Entity)} is called.</p>
 */
public class CsvRepository<T extends Entity> implements Repository<T> {
    private final FileStorageManager storage;
    private final Path file;
    private final String[] header;
    private final Function<String[], T> mapper;
    private final String idPrefix;
    private final Map<String, String[]> rows = new LinkedHashMap<>();

    public CsvRepository(FileStorageManager storage, String fileName, String[] header,
                         Function<String[], T> mapper, String idPrefix) {
        this.storage = storage;
        this.file = storage.resolve(fileName);
        this.header = header.clone();
        this.mapper = mapper;
        this.idPrefix = idPrefix;
        load();
    }

    private void load() {
        List<String> lines = storage.readLines(file);
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            try {
                T entity = mapper.apply(CsvUtil.split(line));
                rows.put(entity.getId(), entity.toCsvRow());
            } catch (RuntimeException e) {
                throw new StorageException("Corrupt row " + (i + 1) + " in " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }
        if (lines.isEmpty()) {
            flush();
        }
    }

    public synchronized Optional<T> findById(String id) {
        String[] row = rows.get(id);
        return row == null ? Optional.empty() : Optional.of(mapper.apply(row.clone()));
    }

    public synchronized List<T> findAll() {
        List<T> out = new ArrayList<>(rows.size());
        for (String[] row : rows.values()) {
            out.add(mapper.apply(row.clone()));
        }
        return out;
    }

    public List<T> findWhere(Predicate<T> filter) {
        List<T> out = new ArrayList<>();
        for (T t : findAll()) {
            if (filter.test(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public synchronized int count() {
        return rows.size();
    }

    public synchronized void save(T entity) {
        rows.put(entity.getId(), entity.toCsvRow());
        flush();
    }

    public synchronized void saveAll(Collection<T> entities) {
        for (T e : entities) {
            rows.put(e.getId(), e.toCsvRow());
        }
        flush();
    }

    /** Replace the complete content (used by backup restore). */
    public synchronized void replaceAll(Collection<T> entities) {
        rows.clear();
        saveAll(entities);
    }

    /** Next free id such as BAT-0007. Callers hold the domain lock between this call and the save. */
    public synchronized String nextId() {
        return nextIds(1).get(0);
    }

    public synchronized List<String> nextIds(int count) {
        int max = 0;
        for (String id : rows.keySet()) {
            int dash = id.lastIndexOf('-');
            try {
                max = Math.max(max, Integer.parseInt(id.substring(dash + 1)));
            } catch (NumberFormatException ignored) {
                // foreign id format - ignore
            }
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("%s-%04d", idPrefix, max + i));
        }
        return ids;
    }

    private void flush() {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(CsvUtil.join(header));
        for (String[] row : rows.values()) {
            lines.add(CsvUtil.join(row));
        }
        storage.writeLinesAtomically(file, lines);
    }
}
