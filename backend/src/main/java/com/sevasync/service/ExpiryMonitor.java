package com.sevasync.service;

import com.sevasync.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background housekeeping on a {@link ScheduledExecutorService}: expires overdue food (so spoiled food is never
 * handed out even when nobody is using the system) and purges expired login sessions.
 */
public class ExpiryMonitor implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExpiryMonitor.class);
    private static final long SESSION_PURGE_EVERY = 60; // ticks

    private final DispatchService dispatches;
    private final AuthService auth;
    private final long intervalMillis;
    private ScheduledExecutorService executor;
    private long ticks;

    public ExpiryMonitor(DispatchService dispatches, AuthService auth, long intervalMillis) {
        this.dispatches = dispatches;
        this.auth = auth;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sevasync-expiry-monitor");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    /** One housekeeping pass (public so tests can run it synchronously). */
    public void tick() {
        try {
            int expired = dispatches.expireOverdue();
            if (expired > 0) {
                LOG.info("Expired {} batch(es)", expired);
            }
            if (ticks++ % SESSION_PURGE_EVERY == 0) {
                auth.purgeExpiredSessions();
            }
        } catch (RuntimeException e) {
            LOG.warn("Expiry monitor pass failed: {}", e.toString());
        }
    }

    @Override
    public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }
}
