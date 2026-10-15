package com.sevasync.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Something that happened in the domain ("DISPATCH_PICKED_UP", "BATCH_LOGGED" ...), published after the change
 * committed. {@code id} is a monotonically increasing sequence assigned by the {@link EventBus}.
 */
public record DomainEvent(long id, String type, String entityType, String entityId, String message,
                          Map<String, Object> data, LocalDateTime at, Audience audience) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("entityType", entityType);
        m.put("entityId", entityId);
        m.put("message", message);
        m.put("data", data);
        m.put("at", at);
        return m;
    }
}
