package com.sevasync.storage;

import com.sevasync.contract.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Generic repository abstraction. Services depend on this interface only, so the storage engine (SQLite today,
 * PostgreSQL tomorrow) can be swapped by writing new implementations without touching business code.
 *
 * @param <T> entity type
 */
public interface Repository<T extends Entity> {

    Optional<T> findById(String id);

    List<T> findAll();

    /** In-memory filter over {@link #findAll()}; implementations override it with SQL where it matters. */
    default List<T> findWhere(Predicate<? super T> filter) {
        return findAll().stream().filter(filter).toList();
    }

    long count();

    /** Insert or update. */
    void save(T entity);

    default void saveAll(Collection<? extends T> entities) {
        for (T e : entities) {
            save(e);
        }
    }
}
