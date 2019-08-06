package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.server.Preconditions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbStorage implements Storage<RocksDbStorageHandle> {
    private static final Logger logger = LoggerFactory.getLogger(RocksDbStorage.class);
    private static final String DEFAULT_PREFIX_FOR_DB_NAME = "replicator_topic";

    private final BlockingQueue<TruncateQueueEntry> truncateJobsQueue;
    private final String path;
    private final BackgroundTruncateHandler backgroundTruncateHandler;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final List<ColumnFamilyOptions> cfOptions;
    private final StorageOptions storageOptions;
    private final Map<String, ColumnFamilyHandle> columnFamilyHandleMap;

    private RocksDB db;
    private WriteOptions writeOptions;
    private ReadOptions totalOrderReadOptions;
    private DBOptions dbOptions;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbStorage(StorageOptions options) throws InterruptedException {
        this.storageOptions = options;
        this.path = options.getStoragePath();
        this.cfOptions = new ArrayList<>();
        this.truncateJobsQueue = new LinkedBlockingQueue<>();
        this.backgroundTruncateHandler = new BackgroundTruncateHandler("StorageBackgroundTruncateHandler");
        this.columnFamilyHandleMap = new HashMap<>();
        backgroundTruncateHandler.start();

        initDB();
    }

    private void initDB() throws InterruptedException {
        writeLock.lock();
        try {
            dbOptions = createDefaultRocksDBOptions();
            dbOptions.setCreateIfMissing(true);
            dbOptions.setCreateMissingColumnFamilies(true);
            writeOptions = new WriteOptions();
            writeOptions.setSync(false);
            totalOrderReadOptions = new ReadOptions();
            totalOrderReadOptions.setTotalOrderSeek(true);

            BlockBasedTableConfig tableConfig = new BlockBasedTableConfig(). //
                    setIndexType(IndexType.kHashSearch). // use hash search(btree) for prefix scan.
                    setBlockSize(4 * SizeUnit.KB).//
                    setFilter(new BloomFilter(16, false)). //
                    setCacheIndexAndFilterBlocks(true). //
                    setBlockCacheSize(512 * SizeUnit.MB). //
                    setCacheNumShardBits(8);

            final File dir = new File(path);
            if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Invalid log path, it's a regular file: " + path);
            }

            final ColumnFamilyOptions columnFamilyOptions = createDefaultColumnFamilyOptions();
            if (storageOptions.isDestroyPreviousDbFiles()) {
                try (Options destroyOptions = new Options(dbOptions, columnFamilyOptions)) {
                    RocksDB.destroyDB(path, destroyOptions);
                }
            }

            final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            List<byte[]> columnFamilyNames = listColumnFamilyNames(dbOptions, columnFamilyOptions);
            if (columnFamilyNames.isEmpty()) {
                // only default column family
                columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            } else {
                columnFamilyNames.forEach(name -> {
                            ColumnFamilyOptions options = createDefaultColumnFamilyOptions();
                            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(name, options));
                        }
                );
            }

            final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
            db = RocksDB.open(dbOptions, path, columnFamilyDescriptors, columnFamilyHandles);

            for (ColumnFamilyHandle handle : columnFamilyHandles) {
                String name = new String(handle.getName(), StandardCharsets.UTF_8);
                columnFamilyHandleMap.put(name, handle);
            }
        } catch (final RocksDBException ex) {
            String msg = String.format("Init RocksDb on path %s failed", path);
            shutdown();
            throw new ReplicatorException(msg, ex);
        } finally {
            writeLock.unlock();
        }
    }

    private DBOptions createDefaultRocksDBOptions() {
        // Turn based on https://github.com/facebook/rocksdb/wiki/RocksDB-Tuning-Guide
        final DBOptions opts = new DBOptions();

        // If this value is set to true, then the database will be created if it is
        // missing during {@code RocksDB.open()}.
        opts.setCreateIfMissing(true);

        // If true, missing column families will be automatically created.
        opts.setCreateMissingColumnFamilies(true);

        // Number of open files that can be used by the DB.  You may need to increase
        // this if your database has a large working set. Value -1 means files opened
        // are always kept open.
        opts.setMaxOpenFiles(-1);

        // The maximum number of concurrent background compactions. The default is 1,
        // but to fully utilize your CPU and storage you might want to increase this
        // to approximately number of cores in the system.
        int cpus = Runtime.getRuntime().availableProcessors();
        opts.setMaxBackgroundCompactions(Math.min(cpus, 4));

        // The maximum number of concurrent flush operations. It is usually good enough
        // to set this to 1.
        opts.setMaxBackgroundFlushes(1);

        return opts;
    }

    private ColumnFamilyOptions createDefaultColumnFamilyOptions() {
        ColumnFamilyOptions options = new ColumnFamilyOptions();

        cfOptions.add(options);

        return options;
    }

    private List<byte[]> listColumnFamilyNames(DBOptions dbOptions, ColumnFamilyOptions columnFamilyOptions)
            throws RocksDBException {
        try (Options options = new Options(dbOptions, columnFamilyOptions)) {
            return RocksDB.listColumnFamilies(options, path);
        }
    }

    @Override
    public void shutdown() throws InterruptedException {
        backgroundTruncateHandler.shutdown();
        wakeupTruncateHandler();
        backgroundTruncateHandler.join();

        writeLock.lock();
        try {
            // The shutdown order is matter.
            // 1. close db and column family handles
            closeDB();
            // 2. close internal options.
            closeOptions();
        } finally {
            writeLock.unlock();
        }

    }

    private void closeDB() {
        for (Map.Entry<String, ColumnFamilyHandle> entry : columnFamilyHandleMap.entrySet()) {
            ColumnFamilyHandle handle = entry.getValue();
            handle.close();
        }

        columnFamilyHandleMap.clear();

        if (db != null) {
            db.close();
            db = null;
        }
    }

    private void closeOptions() {
        // 1. close db options
        dbOptions.close();

        // 2. close column family options.
        for (final ColumnFamilyOptions opt : cfOptions) {
            opt.close();
        }
        cfOptions.clear();

        // 3. close write/read options
        if (writeOptions != null) {
            writeOptions.close();
            writeOptions = null;
        }

        if (totalOrderReadOptions != null) {
            totalOrderReadOptions.close();
            totalOrderReadOptions = null;
        }
    }

    @Override
    public SequenceStorage createSequenceStorage(String topic, SequenceOptions sequenceOptions) {
        writeLock.lock();
        try {
            String name = nameInDb(topic);
            ColumnFamilyHandle handle = columnFamilyHandleMap.get(name);
            if (handle == null) {
                ColumnFamilyOptions columnOptions = createDefaultColumnFamilyOptions();
                try {
                    ColumnFamilyDescriptor desp = new ColumnFamilyDescriptor(name.getBytes(StandardCharsets.UTF_8), columnOptions);
                    handle = db.createColumnFamily(desp);
                } catch (RocksDBException ex) {
                    columnOptions.close();
                    String msg = String.format("create sequence storage on topic %s failed", topic);
                    throw new ReplicatorException(msg, ex);
                }
            }

            RocksDbStorageHandle storageHandle = new RocksDbStorageHandle(topic, handle);
            return new RocksDbSequenceStorage(this, storageHandle);
        } finally {
            writeLock.unlock();
        }
    }

    private String nameInDb(String topic) {
        return DEFAULT_PREFIX_FOR_DB_NAME + "_" + topic;
    }

    @Override
    public void dropSequenceStorage(RocksDbStorageHandle handle) {
        writeLock.lock();
        try {
            db.dropColumnFamily(handle.getColumnFamilyHandle());
            columnFamilyHandleMap.remove(handle.getTopic());
        } catch (RocksDBException ex) {
            String msg = String.format("drop sequence storage for topic: %s failed", handle.getTopic());
            throw new ReplicatorException(msg, ex);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long getFirstLogId(RocksDbStorageHandle handle) {
        readLock.lock();
        try (final RocksIterator it = db.newIterator(handle.getColumnFamilyHandle(), totalOrderReadOptions)) {
            it.seekToFirst();
            if (it.isValid()) {
                return Bits.getLong(it.key(), 0);
            }
            return 0L;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastLogId(RocksDbStorageHandle handle) {
        readLock.lock();
        try (final RocksIterator it = db.newIterator(handle.getColumnFamilyHandle(), totalOrderReadOptions)) {
            it.seekToLast();
            if (it.isValid()) {
                return Bits.getLong(it.key(), 0);
            }
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(RocksDbStorageHandle handle, long id, byte[] data) {
        Preconditions.checkArgument(id > 0);

        readLock.lock();
        try {
            db.put(handle.getColumnFamilyHandle(), getKeyBytes(id), data);
        } catch (final RocksDBException e) {
            throw new ReplicatorException("Fail to append entry", e);
        } finally {
            readLock.unlock();
        }
    }

    private byte[] getKeyBytes(long id) {
        final byte[] ks = new byte[8];
        Bits.putLong(ks, 0, id);
        return ks;
    }

    @Override
    public List<LogEntry> getEntries(RocksDbStorageHandle handle, long fromId, int limit) {
        readLock.lock();

        try {
            fromId = Math.max(fromId, 0);

            List<LogEntry> entries = new ArrayList<>(limit);
            RocksIterator it = db.newIterator(handle.getColumnFamilyHandle(), totalOrderReadOptions);
            for (it.seek(getKeyBytes(fromId)); it.isValid() && entries.size() < limit; it.next()) {
                try {
                    LogEntry entry = new LogEntry();
                    entry.deserialize(it.value());
                    entries.add(entry);
                } catch (DeserializationException ex) {
                    logger.error("Bad log entry format for id={}", Bits.getLong(it.key(), 0));
                }
            }
            return entries;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long trimToId(RocksDbStorageHandle handle, long firstIdToKeep) {
        readLock.lock();
        try {
            final long startId = getFirstLogId(handle);
            if (firstIdToKeep > startId) {
                RocksIterator it = db.newIterator(handle.getColumnFamilyHandle(), totalOrderReadOptions);
                it.seek(getKeyBytes(firstIdToKeep));
                if (it.isValid()) {
                    firstIdToKeep = Bits.getLong(it.key(), 0);
                }
                truncatePrefixInBackground(handle, startId, firstIdToKeep);
                return firstIdToKeep;
            }
            return startId;
        } finally {
            readLock.unlock();
        }
    }

    interface TruncateQueueEntry {
    }

    private static class WakeUpJob implements TruncateQueueEntry {
    }

    private static class TruncateJob implements TruncateQueueEntry {
        private long startId;
        private long firstIdToKeep;
        private ColumnFamilyHandle handle;

        public TruncateJob(ColumnFamilyHandle handle, long startId, long firstIdToKeep) {
            this.startId = startId;
            this.firstIdToKeep = firstIdToKeep;
            this.handle = handle;
        }

        long getStartId() {
            return startId;
        }

        long getFirstIdToKeep() {
            return firstIdToKeep;
        }

        ColumnFamilyHandle getHandle() {
            return handle;
        }
    }

    private class BackgroundTruncateHandler extends Thread {
        private volatile boolean shutdown = false;

        BackgroundTruncateHandler(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!shutdown) {
                TruncateQueueEntry entry = null;
                try {
                    entry = truncateJobsQueue.take();
                    if (entry instanceof TruncateJob) {
                        TruncateJob job = (TruncateJob) entry;
                        readLock.lock();
                        try {
                            if (db == null) {
                                break;
                            }
                            db.deleteRange(job.getHandle(), getKeyBytes(job.getStartId()), getKeyBytes(job.getFirstIdToKeep()));
                        } catch (final RocksDBException e) {
                            logger.error("Fail to truncatePrefix {}", job, e);
                        } finally {
                            readLock.unlock();
                        }
                    }
                } catch (InterruptedException ex) {
                    // continue
                } catch (Exception ex) {
                    logger.error("Truncate handler failed for entry {}", entry, ex);
                    break;
                }
            }

            logger.info(getName() + " exit.");
        }

        void shutdown() {
            shutdown = true;
        }
    }

    private void wakeupTruncateHandler() {
        truncateJobsQueue.offer(new WakeUpJob());
    }

    private void truncatePrefixInBackground(RocksDbStorageHandle handle, final long startId, final long firstIdToKeep) {
        truncateJobsQueue.offer(new TruncateJob(handle.getColumnFamilyHandle(), startId, firstIdToKeep));
    }
}
