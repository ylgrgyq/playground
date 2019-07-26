package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.Preconditions;
import com.github.ylgrgyq.replicator.server.ReplicatorException;
import com.github.ylgrgyq.replicator.server.ReplicatorServerOptions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbStorage implements Storage<RocksDbStorageHandle> {
    private static final Logger logger = LoggerFactory.getLogger(RocksDbStorage.class);

    private final BlockingQueue<TruncateQueueEntry> truncateJobsQueue;
    private final String path;
    private final BackgroundTruncateHandler backgroundTruncateHandler;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final List<ColumnFamilyOptions> cfOptions;

    private RocksDB db;
    private WriteOptions writeOptions;
    private ReadOptions totalOrderReadOptions;
    private DBOptions dbOptions;
    private ColumnFamilyHandle defaultHandle;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbStorage(StorageOptions options) throws InterruptedException{
        this.path = options.getStoragePath();
        this.cfOptions = new ArrayList<>();
        this.truncateJobsQueue = new LinkedBlockingQueue<>();
        this.backgroundTruncateHandler = new BackgroundTruncateHandler("StorageBackgroundTruncateHandler");
        backgroundTruncateHandler.start();

        initDB();
    }

    private void initDB() throws InterruptedException{
        writeLock.lock();
        try {
            dbOptions = new DBOptions();
            dbOptions.setCreateIfMissing(true);
            dbOptions.setCreateMissingColumnFamilies(true);
            writeOptions = new WriteOptions();
            writeOptions.setSync(false);
            totalOrderReadOptions = new ReadOptions();
            totalOrderReadOptions.setTotalOrderSeek(true);

            final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();

            BlockBasedTableConfig tableConfig = new BlockBasedTableConfig(). //
                    setIndexType(IndexType.kHashSearch). // use hash search(btree) for prefix scan.
                    setBlockSize(4 * SizeUnit.KB).//
                    setFilter(new BloomFilter(16, false)). //
                    setCacheIndexAndFilterBlocks(true). //
                    setBlockCacheSize(512 * SizeUnit.MB). //
                    setCacheNumShardBits(8);
            final ColumnFamilyOptions options = new ColumnFamilyOptions();
            cfOptions.add(options);

            // default column family
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));

            final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();

            final File dir = new File(path);
            if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Invalid log path, it's a regular file: " + path);
            }
            db = RocksDB.open(dbOptions, path, columnFamilyDescriptors, columnFamilyHandles);

            assert columnFamilyHandles.size() > 0;
            defaultHandle = columnFamilyHandles.get(0);
        } catch (final RocksDBException ex) {
            String msg = String.format("Init RocksDb on path %s failed", path);
            shutdown();
            throw new ReplicatorException(msg, ex);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void shutdown() throws InterruptedException{
        writeLock.lock();
        try {
            backgroundTruncateHandler.shutdown();
            wakeupTruncateHandler();
            backgroundTruncateHandler.join();

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
        if (defaultHandle != null) {
            defaultHandle.close();
            defaultHandle = null;
        }

        if (db != null) {
            db.close();
            db = null;
        }
    }

    private void closeOptions() {
        // 1. close column family options.
        for (final ColumnFamilyOptions opt : cfOptions) {
            opt.close();
        }
        cfOptions.clear();

        // 2. close write/read options
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
    public SequenceStorage createSequenceStorage(String topic, SequenceOptions options) {
        writeLock.lock();
        ColumnFamilyOptions columnOptions = new ColumnFamilyOptions();
        try {
            cfOptions.add(columnOptions);

            ColumnFamilyDescriptor desp = new ColumnFamilyDescriptor(topic.getBytes(StandardCharsets.UTF_8), columnOptions);
            ColumnFamilyHandle handle = db.createColumnFamily(desp);
            RocksDbStorageHandle storageHandle = new RocksDbStorageHandle(handle);
            return new RocksDbSequenceStorage(this, storageHandle);
        } catch (RocksDBException ex) {
            logger.error("create sequence storage on topic {} failed", topic, ex);
            columnOptions.close();
        } finally {
            writeLock.unlock();
        }

        return null;
    }

    @Override
    public long getFirstLogId(RocksDbStorageHandle handle) {
        readLock.lock();
        try (final RocksIterator it = db.newIterator(handle.getColumnFailyHandle(), totalOrderReadOptions)) {
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
        try (final RocksIterator it = db.newIterator(handle.getColumnFailyHandle(), totalOrderReadOptions)) {
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
            db.put(handle.getColumnFailyHandle(), getKeyBytes(id), data);
        } catch (final RocksDBException e) {
            logger.error("Fail to append entry", e);
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
            RocksIterator it = db.newIterator(handle.getColumnFailyHandle(), totalOrderReadOptions);
            for (it.seek(getKeyBytes(fromId)); it.isValid() && entries.size() < limit; it.next()) {
                try {
                    LogEntry entry = LogEntry.parseFrom(it.value());
                    entries.add(entry);
                } catch (InvalidProtocolBufferException ex) {
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
                RocksIterator it = db.newIterator(handle.getColumnFailyHandle(), totalOrderReadOptions);
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

    interface TruncateQueueEntry {}

    private static class WakeUpJob implements TruncateQueueEntry{}

    private static class TruncateJob implements TruncateQueueEntry{
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
        truncateJobsQueue.offer(new TruncateJob(handle.getColumnFailyHandle(), startId, firstIdToKeep));
    }
}
