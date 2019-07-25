package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.Preconditions;
import com.github.ylgrgyq.replicator.server.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.Bits;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbStorage implements Storage<RocksDbStorageHandle> {
    private static final Logger logger = LoggerFactory.getLogger(RocksDbStorage.class);

    private RocksDB db;
    private WriteOptions writeOptions;
    private ReadOptions totalOrderReadOptions;
    private DBOptions dbOptions;
    private final List<ColumnFamilyOptions> cfOptions;
    private ColumnFamilyHandle defaultHandle;
    private String path;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbStorage(String path) {
        this.path = path;
        this.cfOptions = new ArrayList<>();
    }

    @Override
    public boolean init() {
        writeLock.lock();
        try {
            if (db != null) {
                logger.warn("RocksDbStorage already init.");
                return true;
            }

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
            return true;
        } catch (final RocksDBException ex) {
            logger.error("Init RocksDb on path {} failed", path, ex);
            shutdown();
        } finally {
            writeLock.unlock();
        }

        return false;
    }

    @Override
    public void shutdown() {
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
                truncatePrefixInBackground(startId, firstIdToKeep);
                return firstIdToKeep;
            }
            return startId;
        } finally {
            readLock.unlock();
        }
    }

    private void truncatePrefixInBackground(final long startId, final long firstIdToKeep) {
        // delete logs in background.
        new Thread(() -> {
            readLock.lock();
            try {
                if (db == null) {
                    return;
                }
                db.deleteRange(defaultHandle, getKeyBytes(startId), getKeyBytes(firstIdToKeep));
            } catch (final RocksDBException e) {
                logger.error("Fail to truncatePrefix {}", firstIdToKeep, e);
            } finally {
                readLock.unlock();
            }
        }).start();
    }
}
