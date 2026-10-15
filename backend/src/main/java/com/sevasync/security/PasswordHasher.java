package com.sevasync.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 password hashing from the JDK alone. Every password gets its own random 16-byte salt; the
 * iteration count is stored with the hash so it can be raised later without breaking existing accounts.
 */
public final class PasswordHasher {
    /** Production work factor. Tests pass a much smaller value to stay fast. */
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    public record Hash(String hash, String salt, int iterations) {
    }

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
    }

    public Hash hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] dk = derive(password, salt, iterations);
        return new Hash(Base64.getEncoder().encodeToString(dk), Base64.getEncoder().encodeToString(salt), iterations);
    }

    /** Constant-time comparison, so response timing reveals nothing about how much of a guess was right. */
    public boolean verify(String password, Hash stored) {
        byte[] salt = Base64.getDecoder().decode(stored.salt());
        byte[] expected = Base64.getDecoder().decode(stored.hash());
        byte[] actual = derive(password, salt, stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    /** True when the stored hash was made with a weaker work factor than the current one. */
    public boolean needsRehash(Hash stored) {
        return stored.iterations() < iterations;
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 not available", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
