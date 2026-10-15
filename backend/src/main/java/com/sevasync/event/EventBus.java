package com.sevasync.event;

import com.sevasync.database.DatabaseManager;
import com.sevasync.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observer pattern: services publish {@link DomainEvent}s, listeners (the live SSE stream, counters ...) receive
 * them. Publishing inside a transaction is deferred until commit, so nobody is ever told about a change that was
 * rolled back. The last {@value #REPLAY_SIZE} events are kept so a reconnecting client can catch up.
 */
public class EventBus {
    private static final Logger LOG = LoggerFactory.getLogger(EventBus.class);
    public static final int REPLAY_SIZE = 200;

    private final DatabaseManager db;
    private final Clock clock;
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Deque<DomainEvent> recent = new ArrayDeque<>();

    public EventBus(DatabaseManager db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** @return a handle that unsubscribes when closed */
    public AutoCloseable subscribe(EventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    public void publish(String type, String entityType, String entityId, String message, Map<String, Object> data,
                        Audience audience) {
        db.afterCommit(() -> deliver(type, entityType, entityId, message, data, audience));
    }

    /** Events newer than {@code afterId} that are still in the replay buffer. */
    public List<DomainEvent> since(long afterId) {
        synchronized (recent) {
            List<DomainEvent> out = new ArrayList<>();
            for (DomainEvent e : recent) {
                if (e.id() > afterId) {
                    out.add(e);
                }
            }
            return out;
        }
    }

    public long lastId() {
        return sequence.get();
    }

    private void deliver(String type, String entityType, String entityId, String message, Map<String, Object> data,
                         Audience audience) {
        DomainEvent event = new DomainEvent(sequence.incrementAndGet(), type, entityType, entityId, message,
                data == null ? Map.of() : data, TimeUtil.now(clock), audience);
        synchronized (recent) {
            recent.addLast(event);
            while (recent.size() > REPLAY_SIZE) {
                recent.removeFirst();
            }
        }
        for (EventListener l : listeners) {
            try {
                l.onEvent(event);
            } catch (RuntimeException e) {
                LOG.warn("Event listener failed on {}: {}", type, e.toString());
            }
        }
    }
}
