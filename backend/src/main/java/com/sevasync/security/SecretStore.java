package com.sevasync.security;

import com.sevasync.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides the server's secret key (used to HMAC the audit chain). Taken from the {@code SEVASYNC_SECRET}
 * environment variable (base64) when set, otherwise from {@code <data>/secret.key}, which is generated with
 * {@link SecureRandom} on first start. The key never leaves the server.
 */
public final class SecretStore {
    private static final int KEY_BYTES = 32;

    private SecretStore() {
    }

    public static byte[] load(Path dataDir, String envValue) {
        if (envValue != null && !envValue.isBlank()) {
            byte[] key = Base64.getDecoder().decode(envValue.trim());
            if (key.length < 16) {
                throw new IllegalArgumentException("SEVASYNC_SECRET must decode to at least 16 bytes");
            }
            return key;
        }
        Path file = dataDir.resolve("secret.key");
        try {
            if (Files.exists(file)) {
                return Base64.getDecoder().decode(Files.readString(file).trim());
            }
            byte[] key = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(key);
            Files.createDirectories(dataDir);
            Path tmp = file.resolveSibling("secret.key.tmp");
            Files.writeString(tmp, Base64.getEncoder().encodeToString(key));
            restrictToOwner(tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new StorageException("Cannot read or create " + file + ": " + e.getMessage(), e);
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system (e.g. Windows): the user profile's ACLs apply instead
        }
    }
}
