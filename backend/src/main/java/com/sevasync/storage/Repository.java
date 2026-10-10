package com.sevasync.storage;

import com.sevasync.contract.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Generic repository abstraction for entity persistence.
 * Decouples services from concrete storage mechanisms (SQLite, CSV, in-memory).
 *
 * @param <T> entity type
 */
public interface Repository<T extends Entity> {

    Optional<T> findById(String id);

    List<T> findAll();

    List<T> findWhere(Predicate<T> filter);

    int count();

    void save(T entity);

    void saveAll(Collection<T> entities);

    /**
     * Replace all entities (used by restore or bulk reset).
     */
    void replaceAll(Collection<T> entities);

    /**
     * Generate the next sequential ID with prefix (e.g., DON-0001, BAT-0001).
     */
    String nextId();

    /**
     * Generate batch IDs.
     */
    List<String> nextIds(int count);
}
