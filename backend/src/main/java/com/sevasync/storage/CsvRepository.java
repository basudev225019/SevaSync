package com.sevasync.storage;

import com.sevasync.contract.Entity;
import com.sevasync.util.CsvUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat-file archive of entities as CSV, built on {@link FileStorageManager} (BufferedReader / BufferedWriter).
 * Used for human-readable exports that open in any spreadsheet; the live data stays in SQLite.
 *
 * <p>Columns come from each entity's {@link Entity#toMap()}; nested objects are flattened ({@code location.lat}),
 * lists are joined with {@code |}.
 *
 * @param <T> entity type
 */
public class CsvRepository<T extends Entity> {
    private final FileStorageManager files;
    private final Path file;

    public CsvRepository(FileStorageManager files, Path file) {
        this.files = files;
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /** Writes all entities atomically (temp file + move) and returns the number of data rows. */
    public int writeAll(Collection<? extends T> entities) {
        List<Map<String, String>> rows = new ArrayList<>();
        Set<String> header = new LinkedHashSet<>();
        for (T e : entities) {
            Map<String, String> flat = new LinkedHashMap<>();
            flatten("", e.toMap(), flat);
            header.addAll(flat.keySet());
            rows.add(flat);
        }
        List<String> lines = new ArrayList<>(rows.size() + 1);
        String[] cols = header.toArray(new String[0]);
        lines.add(CsvUtil.join(cols));
        for (Map<String, String> row : rows) {
            String[] fields = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                fields[i] = row.getOrDefault(cols[i], "");
            }
            lines.add(CsvUtil.join(fields));
        }
        files.writeLinesAtomically(file, lines);
        return rows.size();
    }

    /** Reads the file back as one map per row (column name to raw value). */
    public List<Map<String, String>> readRows() {
        List<String> lines = files.readLines(file);
        List<Map<String, String>> out = new ArrayList<>();
        if (lines.isEmpty()) {
            return out;
        }
        String[] header = CsvUtil.split(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] f = CsvUtil.split(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                row.put(header[c], c < f.length ? f[c] : "");
            }
            out.add(row);
        }
        return out;
    }

    private static void flatten(String prefix, Map<String, Object> map, Map<String, String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) nested;
                flatten(key, m, out);
            } else if (v instanceof Collection<?> c) {
                List<String> parts = new ArrayList<>();
                c.forEach(x -> parts.add(String.valueOf(x)));
                out.put(key, String.join("|", parts));
            } else {
                out.put(key, v == null ? "" : String.valueOf(v));
            }
        }
    }
}
