package com.sevasync.api;

import com.sevasync.event.DomainEvent;
import com.sevasync.event.EventBus;
import com.sevasync.exception.AuthenticationException;
import com.sevasync.model.User;
import com.sevasync.security.AuthService;
import com.sevasync.util.Json;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code GET /api/events}: a Server-Sent Events stream of the domain events the caller is allowed to see.
 * Authenticates with the normal bearer header (the frontend reads the stream with {@code fetch}, so the token never
 * goes into a URL), replays missed events from {@code Last-Event-ID}, sends a heartbeat every 15 s and re-checks
 * the session every minute so a signed-out or suspended user is disconnected.
 */
final class EventStream {
    private static final Logger LOG = LoggerFactory.getLogger(EventStream.class);
    static final int MAX_CLIENTS = 48;
    private static final long HEARTBEAT_SECONDS = 15;
    private static final long RECHECK_MILLIS = 60_000;

    private final EventBus bus;
    private final AuthService auth;
    private final AtomicInteger clients = new AtomicInteger();
    private volatile boolean stopping;

    EventStream(EventBus bus, AuthService auth) {
        this.bus = bus;
        this.auth = auth;
    }

    int clientCount() {
        return clients.get();
    }

    void stop() {
        stopping = true;
    }

    /** Streams until the client disconnects, the session ends or the server stops. */
    void handle(HttpExchange ex) throws IOException {
        String token = Request.bearerToken(ex);
        User user;
        try {
            user = auth.authenticate(token);
        } catch (AuthenticationException e) {
            ApiServer.sendJson(ex, 401, ApiServer.error(e.getMessage(), e.code()));
            return;
        }
        if (clients.incrementAndGet() > MAX_CLIENTS) {
            clients.decrementAndGet();
            ApiServer.sendJson(ex, 503, ApiServer.error("Too many live connections, retry shortly", "BUSY"));
            return;
        }
        BlockingQueue<DomainEvent> queue = new LinkedBlockingQueue<>(500);
        final User viewer = user;
        AutoCloseable subscription = bus.subscribe(e -> {
            if (e.audience().includes(viewer)) {
                queue.offer(e); // drop rather than block the publisher if this client is hopelessly slow
            }
        });
        try {
            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
            ex.getResponseHeaders().set("X-Accel-Buffering", "no");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                write(out, "retry: 3000\n\n");
                long lastId = parseLong(ex.getRequestHeaders().getFirst("Last-Event-ID"));
                if (lastId > 0) {
                    for (DomainEvent e : bus.since(lastId)) {
                        if (e.audience().includes(viewer)) {
                            send(out, e);
                        }
                    }
                }
                write(out, "event: ready\ndata: {\"lastEventId\":" + bus.lastId() + "}\n\n");
                long lastCheck = System.currentTimeMillis();
                while (!stopping) {
                    DomainEvent e = queue.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                    if (e != null) {
                        send(out, e);
                    } else {
                        write(out, ": keep-alive\n\n");
                    }
                    if (System.currentTimeMillis() - lastCheck > RECHECK_MILLIS) {
                        auth.authenticate(token); // throws when the session ended
                        lastCheck = System.currentTimeMillis();
                    }
                }
            }
        } catch (IOException | AuthenticationException e) {
            LOG.debug("Event stream for {} closed: {}", user.getId(), e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clients.decrementAndGet();
            try {
                subscription.close();
            } catch (Exception ignored) {
                // nothing to clean up
            }
            ex.close();
        }
    }

    private static void send(OutputStream out, DomainEvent e) throws IOException {
        write(out, "id: " + e.id() + "\nevent: " + e.type() + "\ndata: " + Json.write(e.toMap()) + "\n\n");
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
