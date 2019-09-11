package com.github.ylgrgyq.reservoir;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A storage used by {@link ObjectQueueConsumer} or {@link ObjectQueueProducer} to
 * store/fetch the serialized objects.
 */
public interface ObjectQueueStorage extends AutoCloseable {
    /**
     * Store a list of serialized object to storage.
     *
     * @param batch the list of serialized object.
     * @throws StorageException thrown if any error happens in underlying storage medium
     */
    void store(List<byte[]> batch) throws StorageException;

    /**
     * Save commit id of {@link ObjectQueueConsumer} to storage.
     *
     * @param id the id to commit
     * @throws StorageException thrown if any error happens in underlying storage medium
     */
    void commitId(long id) throws StorageException;

    /**
     * Get the latest id successfully passed to {@link #commitId(long)}.
     * Used by {@link ObjectQueueConsumer} to prevent it from consume an object which
     * has committed.
     *
     * @return the latest saved commit id
     * @throws StorageException thrown if any error happens in underlying storage medium
     */
    long getLastCommittedId() throws StorageException;

    /**
     * Blocking to fetch a list of serialized objects with their assigned id starting after
     * {@code fromId} from this storage.
     * Returned at most {@code limit} objects and at least one object was fetched.
     *
     * @param fromId the id from which to fetch, exclusive.
     * @param limit  the maximum size of the returned list.
     * @return A list of serialized objects with their assigned id. Every {@link ObjectWithId}
     * in this list should have an id greater than {@code fromId}
     * @throws InterruptedException thrown when the calling thread was interrupted
     * @throws StorageException     thrown if any error happens in underlying storage medium
     */
    List<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException;

    /**
     * Blocking to fetch a list of serialized objects with their assigned id starting after
     * {@code fromId} from this storage with a limited waiting time.
     * Returned at most {@code limit} objects when at least one object was fetched or
     * the timeout was reached.
     *
     * @param fromId  the id from which to fetch, exclusive.
     * @param limit   the maximum size of the returned list.
     * @param timeout the maximum blocking time to fetch objects
     * @param unit    the unit of time for {@code timeout} parameter
     * @return A list of serialized objects with their assigned id. Every {@link ObjectWithId}
     * in this list should have an id greater than {@code fromId}
     * @throws InterruptedException thrown when the calling thread was interrupted
     * @throws StorageException     thrown if any error happens in underlying storage medium
     */
    List<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException;
}
