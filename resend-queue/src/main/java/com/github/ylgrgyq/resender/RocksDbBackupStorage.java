package com.github.ylgrgyq.resender;

import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Objects.requireNonNull;

public final class RocksDbBackupStorage implements ProducerStorage, ConsumerStorage {
    private static final Logger logger = LoggerFactory.getLogger(RocksDbBackupStorage.class);
    private static final String DEFAULT_QUEUE_NAME = "resend_queue";
    private static final byte[] CONSUMER_COMMIT_ID_META_KEY = "consumer_committed_id".getBytes(StandardCharsets.UTF_8);

    private final BlockingQueue<TruncateQueueEntry> truncateJobsQueue;
    private final String path;
    private final BackgroundTruncateHandler backgroundTruncateHandler;

    private final List<ColumnFamilyOptions> cfOptions;
    private final long readRetryIntervalMillis;

    private final RocksDB db;
    private final ColumnFamilyHandle defaultColumnFamilyHandle;
    private final ColumnFamilyHandle columnFamilyHandle;
    private final WriteOptions writeOptions;
    private final ReadOptions totalOrderReadOptions;
    private final DBOptions dbOptions;

    private volatile boolean stopped;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbBackupStorage(String path, boolean destroyPreviousDbFiles, long readRetryIntervalMillis) throws InterruptedException {
        this.path = path;
        this.cfOptions = new ArrayList<>();
        this.truncateJobsQueue = new LinkedBlockingQueue<>();
        this.backgroundTruncateHandler = new BackgroundTruncateHandler("StorageBackgroundTruncateHandler");
        this.backgroundTruncateHandler.start();
        this.readRetryIntervalMillis = readRetryIntervalMillis;

        try {
            final DBOptions dbOptions = createDefaultRocksDBOptions();
            dbOptions.setCreateMissingColumnFamilies(true);
            dbOptions.setCreateIfMissing(true);
            this.dbOptions = dbOptions;

            final WriteOptions writeOptions = new WriteOptions();
            writeOptions.setSync(false);
            this.writeOptions = writeOptions;

            final ReadOptions totalOrderReadOptions = new ReadOptions();
            totalOrderReadOptions.setTotalOrderSeek(true);
            this.totalOrderReadOptions = totalOrderReadOptions;

            final BlockBasedTableConfig tableConfig = new BlockBasedTableConfig(). //
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
            if (destroyPreviousDbFiles) {
                try (Options destroyOptions = new Options(dbOptions, columnFamilyOptions)) {
                    RocksDB.destroyDB(path, destroyOptions);
                }
            }

            final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            ColumnFamilyOptions options = createDefaultColumnFamilyOptions();
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(DEFAULT_QUEUE_NAME.getBytes(), options));

            final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
            this.db = RocksDB.open(dbOptions, path, columnFamilyDescriptors, columnFamilyHandles);
            this.defaultColumnFamilyHandle = columnFamilyHandles.get(0);
            this.columnFamilyHandle = columnFamilyHandles.get(1);
        } catch (final RocksDBException ex) {
            String msg = String.format("init RocksDb on path %s failed", path);
            close();
            throw new IllegalStateException(msg, ex);
        }
    }

    @Override
    public void commitId(long id) {
        try {
            final byte[] bs = new byte[8];
            Bits.putLong(bs, 0, id);
            db.put(defaultColumnFamilyHandle, writeOptions, CONSUMER_COMMIT_ID_META_KEY, bs);
        } catch (RocksDBException ex) {
            throw new IllegalStateException("fail to commit id: " + id, ex);
        }
    }

    @Override
    public long getLastCommittedId() {
        try {
            final byte[] commitIdInBytes = db.get(defaultColumnFamilyHandle, totalOrderReadOptions, CONSUMER_COMMIT_ID_META_KEY);
            return Bits.getLong(commitIdInBytes, 0);
        } catch (RocksDBException ex) {
            throw new IllegalStateException("fail to get last committed id: ", ex);
        }
    }

    @Override
    public long getLastProducedId() {
        try (final RocksIterator it = db.newIterator(columnFamilyHandle, totalOrderReadOptions)) {
            it.seekToLast();
            if (it.isValid()) {
                return Bits.getLong(it.key(), 0);
            }
            return 0;
        }
    }

    @Override
    public Collection<PayloadWithId> read(long fromId, int limit) throws InterruptedException {
        fromId = Math.max(fromId, 0);

        final List<PayloadWithId> entries = new ArrayList<>(limit);
        while (true) {
            try (RocksIterator it = db.newIterator(columnFamilyHandle, totalOrderReadOptions)) {
                for (it.seek(getKeyBytes(fromId)); it.isValid() && entries.size() < limit; it.next()) {
                    final long id = Bits.getLong(it.key(), 0);
                    final PayloadWithId entry = new PayloadWithId(id, it.value());
                    entries.add(entry);
                }
            }

            if (entries.isEmpty()) {
                Thread.sleep(readRetryIntervalMillis);
            } else {
                break;
            }
        }
        return Collections.unmodifiableCollection(entries);
    }

    @Override
    public void store(Collection<PayloadWithId> queue) {
        requireNonNull(queue, "queue");

        try {
            final WriteBatch batch = new WriteBatch();
            for (PayloadWithId e : queue) {
                batch.put(columnFamilyHandle, getKeyBytes(e.getId()), e.getPayload());
            }
            db.write(writeOptions, batch);
        } catch (final RocksDBException e) {
            throw new IllegalStateException("fail to append entry", e);
        }
    }

    @Override
    public void close() throws InterruptedException {
        backgroundTruncateHandler.shutdown();
        wakeupTruncateHandler();
        backgroundTruncateHandler.join();

        // The shutdown order is matter.
        // 1. close db and column family handles
        closeDB();
        // 2. close internal options.
        closeOptions();
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
        final ColumnFamilyOptions options = new ColumnFamilyOptions();

        cfOptions.add(options);

        return options;
    }

    private List<byte[]> listColumnFamilyNames(DBOptions dbOptions, ColumnFamilyOptions columnFamilyOptions)
            throws RocksDBException {
        try (Options options = new Options(dbOptions, columnFamilyOptions)) {
            return RocksDB.listColumnFamilies(options, path);
        }
    }

    private void closeDB() {
        ColumnFamilyHandle handle = defaultColumnFamilyHandle;
        if (handle != null) {
            handle.close();
        }
        handle = columnFamilyHandle;
        if (handle != null) {
            handle.close();
        }

        if (db != null) {
            db.close();
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
        }

        if (totalOrderReadOptions != null) {
            totalOrderReadOptions.close();
        }
    }


    private byte[] getKeyBytes(long id) {
        final byte[] ks = new byte[8];
        Bits.putLong(ks, 0, id);
        return ks;
    }


    public long trimToId(long firstIdToKeep) {
//        readLock.lock();
//        try {
//            final long startId = getFirstLogId(handle);
//            if (firstIdToKeep > startId) {
//                RocksIterator it = db.newIterator(handle.getColumnFamilyHandle(), totalOrderReadOptions);
//                it.seek(getKeyBytes(firstIdToKeep));
//                if (it.isValid()) {
//                    firstIdToKeep = Bits.getLong(it.key(), 0);
//                }
//                truncatePrefixInBackground(handle, startId, firstIdToKeep);
//                return firstIdToKeep;
//            }
//            return startId;
//        } finally {
//            readLock.unlock();
//        }
        return 0;
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
                        try {
                            if (db == null) {
                                break;
                            }
                            db.deleteRange(job.getHandle(), getKeyBytes(job.getStartId()), getKeyBytes(job.getFirstIdToKeep()));
                        } catch (final RocksDBException e) {
                            logger.error("Fail to truncatePrefix {}", job, e);
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

    private void truncatePrefixInBackground(final long startId, final long firstIdToKeep) {
        truncateJobsQueue.offer(new TruncateJob(columnFamilyHandle, startId, firstIdToKeep));
    }


}