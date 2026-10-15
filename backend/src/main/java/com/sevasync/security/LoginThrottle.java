package com.sevasync.security;

import com.sevasync.exception.RateLimitedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window brute-force protection: after {@code maxFailures} failed sign-ins for the same account from the
 * same client within {@code window}, further attempts are refused until the oldest failure leaves the window.
 */
public class LoginThrottle {
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public void check(String key) throws RateLimitedException {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return;
        }
        synchronized (q) {
            prune(q);
            if (q.size() >= maxFailures) {
                long wait = Math.max(1, Duration.between(clock.instant(), q.peekFirst().plus(window)).toSeconds());
                throw new RateLimitedException("Too many failed sign-in attempts. Try again in " + wait + " s", wait);
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = clock.instant().minus(window);
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
            q.removeFirst();
        }
    }
}
