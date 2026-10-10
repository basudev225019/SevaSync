package com.sevasync.storage;

import com.sevasync.exception.StorageException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level flat-file access (.csv / .txt) using BufferedReader and BufferedWriter.
 * Whole-file writes go to a temp file first and are then moved into place, so a crash
 * can never leave a half-written data file behind.
 */
public class FileStorageManager {
    private final Path dataDir;

    public FileStorageManager(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath();
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new StorageException("Cannot create data directory " + this.dataDir, e);
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path resolve(String fileName) {
        return dataDir.resolve(fileName);
    }

    public List<String> readLines(Path file) {
        List<String> lines = new ArrayList<>();
        if (!Files.exists(file)) {
            return lines;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new StorageException("Cannot read " + file.getFileName(), e);
        }
        return lines;
    }

    public void writeLinesAtomically(Path file, List<String> lines) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Cannot write " + file.getFileName(), e);
        }
        try {
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Cannot replace " + file.getFileName(), e);
        }
    }

    public void appendLine(Path file, String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new StorageException("Cannot append to " + file.getFileName(), e);
        }
    }
}
