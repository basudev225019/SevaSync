package com.sevasync.service;

/**
 * Background thread that periodically expires overdue batches, so spoiled food is never handed out even if
 * nobody is using the system. Demonstrates the Java thread model (Runnable, daemon thread, interrupt, join).
 */
public class ExpiryMonitor implements Runnable {
    private final DispatchService dispatchService;
    private final long intervalMillis;
    private volatile boolean running;
    private Thread thread;

    public ExpiryMonitor(DispatchService dispatchService, long intervalMillis) {
        this.dispatchService = dispatchService;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void start() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        running = true;
        thread = new Thread(this, "sevasync-expiry-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running) {
            try {
                dispatchService.expireOverdue();
            } catch (RuntimeException e) {
                System.err.println("[expiry-monitor] " + e.getMessage());
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
