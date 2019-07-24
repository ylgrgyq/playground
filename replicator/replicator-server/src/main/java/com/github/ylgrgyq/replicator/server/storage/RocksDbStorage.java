package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbStorage implements Storage {
    private static final Logger logger = LoggerFactory.getLogger(RocksDbStorage.class);

    private RocksDB db;
    private WriteOptions writeOptions;
    private ReadOptions totalOrderReadOptions;
    private DBOptions dbOptions;
    private final List<ColumnFamilyOptions> cfOptions = new ArrayList<>();
    private ColumnFamilyHandle defaultHandle;
    private String path;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private volatile long firstLogId = 1;

    private volatile boolean hasLoadFirstLogId;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDbStorage(String path) {
        this.path = path;
    }

    @Override
    public void init() {
        writeLock.lock();
        try {
            if (this.db != null) {
                logger.warn("RocksDbStorage already init.");
                return;
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

            final File dir = new File(this.path);
            if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Invalid log path, it's a regular file: " + this.path);
            }
            this.db = RocksDB.open(this.dbOptions, this.path, columnFamilyDescriptors, columnFamilyHandles);

            this.defaultHandle = columnFamilyHandles.get(0);
        } catch (final RocksDBException e) {
            logger.error("Fail to init RocksDBLogStorage, path={}", this.path, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void shutdown() {
        writeLock.lock();
        try {
            // The shutdown order is matter.
            // 1. close column family handles
            closeDB();
            // 2. close column family options.
            for (final ColumnFamilyOptions opt : this.cfOptions) {
                opt.close();
            }
            // 3. close options
            this.writeOptions.close();
            this.totalOrderReadOptions.close();
            // 4. help gc.
            this.cfOptions.clear();
            this.db = null;
            this.totalOrderReadOptions = null;
            this.writeOptions = null;
            this.defaultHandle = null;
        } finally {
            writeLock.unlock();
        }

    }

    private void closeDB() {
        this.defaultHandle.close();
        this.db.close();
    }

    @Override
    public long getFirstLogId() {
        readLock.lock();
        try {
            if (hasLoadFirstLogId) {
                return firstLogId;
            }

            try (final RocksIterator it = db.newIterator(defaultHandle, totalOrderReadOptions)) {
                it.seekToFirst();
                if (it.isValid()) {
                    final long ret = Bits.getLong(it.key(), 0);
                    setFirstLogId(ret);
                    return ret;
                }
                return 0L;
            }
        } finally {
            readLock.unlock();
        }
    }

    private void setFirstLogId(long id) {
        this.firstLogId = id;
        this.hasLoadFirstLogId = true;
    }

    @Override
    public long getLastLogId() {
        readLock.lock();
        try (final RocksIterator it = db.newIterator(defaultHandle, totalOrderReadOptions)) {
            it.seekToLast();
            if (it.isValid()) {
                return Bits.getLong(it.key(), 0);
            }
            return getFirstLogId();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(long id, byte[] data) {
        Preconditions.checkArgument(id > 0);

        readLock.lock();
        try {
            LogEntry.Builder builder = LogEntry.newBuilder();
            builder.setData(ByteString.copyFrom(data));
            builder.setId(id);
            db.put(defaultHandle, getKeyBytes(id), builder.build().toByteArray());
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
    public List<LogEntry> getEntries(long fromId, int limit) {
        readLock.lock();

        try {
            fromId = Math.max(fromId, 0);

            List<LogEntry> entries = new ArrayList<>(limit);
            RocksIterator it = db.newIterator(defaultHandle, totalOrderReadOptions);
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
    public void trimToId(long firstIdToKeep) {
        readLock.lock();
        try {
            final long startId = getFirstLogId();
            if (firstIdToKeep > startId) {
                RocksIterator it = db.newIterator(defaultHandle, totalOrderReadOptions);
                it.seek(getKeyBytes(firstIdToKeep));
                if (it.isValid()) {
                    firstIdToKeep = Bits.getLong(it.key(), 0);
                }
                setFirstLogId(firstIdToKeep);
                truncatePrefixInBackground(startId, firstIdToKeep);
            }
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
