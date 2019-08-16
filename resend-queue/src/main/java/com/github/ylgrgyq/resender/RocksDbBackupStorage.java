package com.github.ylgrgyq.resender;

import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbBackupStorage<T> implements BackupStorage<PayloadCarrier<T>>{
    private static final Logger logger = LoggerFactory.getLogger(RocksDbBackupStorage.class);
    private static final String DEFAULT_QUEUE_NAME = "resend_queue";

    private final BlockingQueue<TruncateQueueEntry> truncateJobsQueue;
    private final String path;
    private final BackgroundTruncateHandler backgroundTruncateHandler;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final List<ColumnFamilyOptions> cfOptions;

    private RocksDB db;
    private ColumnFamilyHandle defaultColumnFamilyHandle;
    private ColumnFamilyHandle columnFamilyHandle;
    private WriteOptions writeOptions;
    private ReadOptions totalOrderReadOptions;
    private DBOptions dbOptions;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbBackupStorage(String path, boolean destroyPreviousDbFiles) throws InterruptedException {
        this.path = path;
        this.cfOptions = new ArrayList<>();
        this.truncateJobsQueue = new LinkedBlockingQueue<>();
        this.backgroundTruncateHandler = new BackgroundTruncateHandler("StorageBackgroundTruncateHandler");
        this.backgroundTruncateHandler.start();

        initDB(destroyPreviousDbFiles);
    }

    @Override
    public long getLastId() {
        readLock.lock();
        try (final RocksIterator it = db.newIterator(columnFamilyHandle, totalOrderReadOptions)) {
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
    public Collection<? extends PayloadCarrier<T>> read(long fromId, int limit) {
        readLock.lock();

        try {
            fromId = Math.max(fromId, 0);

            List<PayloadCarrier<T>> entries = new ArrayList<>(limit);
            RocksIterator it = db.newIterator(columnFamilyHandle, totalOrderReadOptions);
            for (it.seek(getKeyBytes(fromId)); it.isValid() && entries.size() < limit; it.next()) {
                try {
                    PayloadCarrier<T> entry = new PayloadCarrier<>(it.value());
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
    public void store(Collection<? extends PayloadCarrier<T>> queue) {
        Objects.requireNonNull(queue, "queue");

        readLock.lock();
        try {
            WriteBatch batch = new WriteBatch();
            for(PayloadCarrier<T> e : queue) {
                batch.put(columnFamilyHandle, getKeyBytes(e.getId()), null);
            }
            db.write(writeOptions, batch);
        } catch (final RocksDBException e) {
            throw new IllegalStateException("fail to append entry", e);
        } finally {
            readLock.unlock();
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

    private void initDB(boolean destroyPreviousDbFiles) throws InterruptedException {
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
            db = RocksDB.open(dbOptions, path, columnFamilyDescriptors, columnFamilyHandles);
            defaultColumnFamilyHandle = columnFamilyHandles.get(0);
            columnFamilyHandle = columnFamilyHandles.get(1);
        } catch (final RocksDBException ex) {
            String msg = String.format("init RocksDb on path %s failed", path);
            shutdown();
            throw new IllegalStateException(msg, ex);
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



    private void closeDB() {
        ColumnFamilyHandle handle = columnFamilyHandle;
        if (handle != null) {
            handle.close();
        }

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

    private void truncatePrefixInBackground(final long startId, final long firstIdToKeep) {
        truncateJobsQueue.offer(new TruncateJob(columnFamilyHandle, startId, firstIdToKeep));
    }
    
    
}
