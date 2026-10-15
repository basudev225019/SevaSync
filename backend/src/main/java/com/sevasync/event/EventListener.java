package com.sevasync.event;

/** Observer of domain events. Implementations must be fast and must not throw. */
@FunctionalInterface
public interface EventListener {
    void onEvent(DomainEvent event);
}
