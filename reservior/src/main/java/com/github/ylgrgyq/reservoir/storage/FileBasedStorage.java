package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.Bits;
import com.github.ylgrgyq.reservoir.ObjectQueueStorage;
import com.github.ylgrgyq.reservoir.ObjectWithId;
import com.github.ylgrgyq.reservoir.StorageException;
import com.github.ylgrgyq.reservoir.storage.FileName.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public final class FileBasedStorage implements ObjectQueueStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedStorage.class.getName());

    private final ExecutorService sstableWriterPool;
    private final String baseDir;
    private final TableCache tableCache;
    private final Manifest manifest;
    private final long readRetryIntervalMillis;
    private final FileLock storageLock;

    @Nullable
    private LogWriter dataLogWriter;
    private volatile int dataLogFileNumber;
    @Nullable
    private LogWriter consumerCommitLogWriter;
    private volatile int consumerCommitLogFileNumber;
    private long firstIdInStorage;
    private long lastIdInStorage;
    private long lastCommittedId;
    private long lastTryTruncateTime;
    private long truncateIntervalNanos;
    private Memtable mm;
    @Nullable
    private volatile Memtable imm;
    private volatile StorageStatus status;

    FileBasedStorage(FileBasedStorageBuilder builder) throws StorageException {
        requireNonNull(builder, "builder");
        final String storageBaseDir = builder.getStorageBaseDir();
        Path baseDirPath = Paths.get(storageBaseDir);

        if (Files.exists(baseDirPath) && !Files.isDirectory(baseDirPath)) {
            throw new IllegalArgumentException("\"" + storageBaseDir + "\" must be a directory");
        }

        this.sstableWriterPool = builder.getFlushMemtableExecutorService();
        this.readRetryIntervalMillis = builder.getReadRetryIntervalMillis();
        this.mm = new Memtable();
        this.baseDir = storageBaseDir;
        this.firstIdInStorage = -1;
        this.lastIdInStorage = -1;
        this.lastCommittedId = Long.MIN_VALUE;
        this.status = StorageStatus.INIT;
        this.tableCache = new TableCache(baseDir);
        this.manifest = new Manifest(baseDir);
        this.truncateIntervalNanos = TimeUnit.MILLISECONDS.toNanos(builder.getTruncateIntervalMillis());

        try {
            createStorageDir();

            this.storageLock = lockStorage(storageBaseDir);

            logger.debug("Start init storage under {}", storageBaseDir);

            final ManifestRecord record = ManifestRecord.newPlainRecord();
            final Path currentFilePath = Paths.get(storageBaseDir, FileName.getCurrentFileName());
            if (Files.exists(currentFilePath)) {
                recoverStorage(currentFilePath, record);
            }

            if (this.dataLogWriter == null) {
                this.dataLogWriter = createNewDataLogWriter();
            }

            if (this.consumerCommitLogWriter == null) {
                this.consumerCommitLogWriter = createConsumerCommitLogWriter();
            }

            record.setDataLogFileNumber(this.dataLogFileNumber);
            record.setConsumerCommitLogFileNumber(this.consumerCommitLogFileNumber);
            this.manifest.logRecord(record);

            this.firstIdInStorage = this.manifest.getFirstId();
            if (this.firstIdInStorage < 0) {
                this.firstIdInStorage = this.mm.firstId();
            }

            this.lastIdInStorage = this.mm.lastId();
            if (this.lastIdInStorage < 0) {
                this.lastIdInStorage = this.manifest.getLastId();
            }

            this.lastTryTruncateTime = System.nanoTime();
            this.status = StorageStatus.OK;
        } catch (IOException | StorageException t) {
            throw new IllegalStateException("init storage failed", t);
        } catch (Exception ex) {
            throw new StorageException(ex);
        } finally {
            if (status != StorageStatus.OK) {
                status = StorageStatus.ERROR;
                try {
                    releaseStorageLock();
                } catch (IOException ex) {
                    logger.error("Release storage lock failed", ex);
                }
            }
        }
    }

    @Override
    public synchronized void commitId(long id) throws StorageException {
        try {
            if (id > lastCommittedId) {
                assert consumerCommitLogWriter != null;
                final byte[] bs = new byte[8];
                Bits.putLong(bs, 0, id);
                consumerCommitLogWriter.append(bs);
                lastCommittedId = id;

                if (System.nanoTime() - lastTryTruncateTime > truncateIntervalNanos) {
                    tryTruncate();
                }
            }
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
    }

    @Override
    public synchronized long getLastCommittedId() {
        return lastCommittedId;
    }

    @Override
    public List<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException {
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        List<ObjectWithId> entries;
        while (true) {
            entries = doFetch(fromId, limit);

            if (!entries.isEmpty()) {
                break;
            }

            Thread.sleep(readRetryIntervalMillis);
        }

        return Collections.unmodifiableList(entries);
    }

    @Override
    public List<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException {

        final long end = System.nanoTime() + unit.toNanos(timeout);
        List<ObjectWithId> entries;
        while (true) {
            entries = doFetch(fromId, limit);

            if (!entries.isEmpty()) {
                break;
            }

            final long remain = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());
            if (remain <= 0) {
                break;
            }

            Thread.sleep(Math.min(remain, readRetryIntervalMillis));
        }

        return Collections.unmodifiableList(entries);
    }

    @Override
    public synchronized long getLastProducedId() {
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        assert mm.isEmpty() || mm.lastId() == lastIdInStorage :
                String.format("actual lastIndex:%s lastIndexInMm:%s", lastIdInStorage, mm.lastId());
        return lastIdInStorage;
    }

    @Override
    public synchronized void store(List<ObjectWithId> batch) throws StorageException {
        requireNonNull(batch, "batch");
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        if (batch.isEmpty()) {
            logger.warn("append with empty entries");
            return;
        }

        if (firstIdInStorage < 0) {
            final ObjectWithId first = batch.get(0);
            firstIdInStorage = first.getId();
        }

        try {
            long lastIndex = getLastProducedId();
            for (ObjectWithId e : batch) {
                if (e.getId() <= lastIndex) {
                    throw new IllegalArgumentException("data being appended is not monotone increasing: " + batch);
                }

                lastIndex = e.getId();
                if (makeRoomForEntry(false)) {
                    assert dataLogWriter != null;
                    dataLogWriter.append(encodeObjectWithId(e));
                    mm.add(e);
                    lastIdInStorage = e.getId();
                } else {
                    throw new StorageException("no more room to storage data");
                }
            }
        } catch (IOException ex) {
            throw new StorageException("append log on file based storage failed", ex);
        }
    }

    @Override
    public void close() throws Exception {
        if (status == StorageStatus.SHUTTING_DOWN) {
            return;
        }

        try {
            status = StorageStatus.SHUTTING_DOWN;
            sstableWriterPool.shutdown();

            logger.debug("shutting file based storage down");
            sstableWriterPool.shutdownNow();

            if (dataLogWriter != null) {
                dataLogWriter.close();
            }

            if (consumerCommitLogWriter != null) {
                consumerCommitLogWriter.close();
            }

            manifest.close();

            tableCache.evictAll();

            releaseStorageLock();
            logger.debug("file based storage shutdown successfully");


        } catch (RejectedExecutionException ex) {
            throw new StorageException(ex);
        }
    }

    private void createStorageDir() throws IOException {
        Path storageDirPath = Paths.get(baseDir);
        try {
            Files.createDirectories(storageDirPath);
        } catch (FileAlreadyExistsException ex) {
            // we don't care if the dir is already exists
        }
    }

    private FileLock lockStorage(String baseDir) throws IOException {
        final Path lockFilePath = Paths.get(baseDir, FileName.getLockFileName());
        final FileChannel lockChannel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final FileLock lock;
        try {
            lock = lockChannel.tryLock();
            if (lock == null) {
                throw new IllegalStateException("failed to lock directory: " + baseDir);
            }
        } catch (IOException ex) {
            lockChannel.close();
            throw ex;
        }

        return lock;
    }

    private void releaseStorageLock() throws IOException {
        if (storageLock != null) {
            final Channel channel = storageLock.acquiredBy();
            try {
                storageLock.release();
            } finally {
                if (channel.isOpen()) {
                    channel.close();
                }
            }
        }
    }

    private synchronized void recoverStorage(Path currentFilePath, ManifestRecord record) throws IOException, StorageException {
        assert Files.exists(currentFilePath);
        final String currentManifestFileName = new String(Files.readAllBytes(currentFilePath), StandardCharsets.UTF_8);
        if (currentManifestFileName.isEmpty()) {
            throw new StorageException("empty CURRENT file in storage dir: " + baseDir);
        }

        manifest.recover(currentManifestFileName);

        recoverFromDataLogFiles(record);
        recoverLastConsumerCommittedId();
    }

    private void recoverFromDataLogFiles(ManifestRecord record) throws IOException, StorageException {
        final int dataLogFileNumber = manifest.getDataLogFileNumber();
        final List<FileName.FileNameMeta> dataLogFileMetas =
                FileName.getFileNameMetas(baseDir, fileMeta -> fileMeta.getType() == FileType.Log
                        && fileMeta.getFileNumber() >= dataLogFileNumber);

        for (int i = 0; i < dataLogFileMetas.size(); ++i) {
            final FileName.FileNameMeta fileMeta = dataLogFileMetas.get(i);
            recoverMemtableFromDataLogFiles(fileMeta.getFileNumber(), record, i == dataLogFileMetas.size() - 1);
        }
    }

    private void recoverMemtableFromDataLogFiles(int fileNumber, ManifestRecord record, boolean lastLogFile) throws IOException, StorageException {
        final Path logFilePath = Paths.get(baseDir, FileName.getLogFileName(fileNumber));
        if (!Files.exists(logFilePath)) {
            logger.warn("Log file {} was deleted. We can't recover memtable from it.", logFilePath);
            return;
        }

        final FileChannel ch = FileChannel.open(logFilePath, StandardOpenOption.READ);
        long readEndPosition;
        boolean noNewSSTable = true;
        Memtable recoveredMm = null;
        try (LogReader reader = new LogReader(ch, true)) {
            while (true) {
                List<byte[]> logOpt = reader.readLog();
                if (!logOpt.isEmpty()) {
                    final ObjectWithId e = decodeObjectWithId(logOpt);
                    if (recoveredMm == null) {
                        recoveredMm = new Memtable();
                    }
                    recoveredMm.add(e);
                    if (recoveredMm.getMemoryUsedInBytes() > Constant.kMaxMemtableSize) {
                        final SSTableFileMetaInfo meta = writeMemTableToSSTable(recoveredMm);
                        record.addMeta(meta);
                        noNewSSTable = false;
                        recoveredMm = null;
                    }
                } else {
                    break;
                }
            }

            readEndPosition = ch.position();

            if (lastLogFile && noNewSSTable) {
                final FileChannel logFile = FileChannel.open(logFilePath, StandardOpenOption.WRITE);
                assert dataLogWriter == null;
                assert dataLogFileNumber == 0;
                dataLogWriter = new LogWriter(logFile, readEndPosition);
                dataLogFileNumber = fileNumber;
                if (recoveredMm != null) {
                    mm = recoveredMm;
                    recoveredMm = null;
                }
            }
        } catch (BadRecordException ex) {
            logger.warn("got \"{}\" record in log file:\"{}\". ", ex.getType(), logFilePath);
        }

        if (recoveredMm != null) {
            final SSTableFileMetaInfo meta = writeMemTableToSSTable(recoveredMm);
            record.addMeta(meta);
        }
    }

    private synchronized void recoverLastConsumerCommittedId() throws IOException, StorageException {
        final int fileNumber = manifest.getConsumerCommittedIdLogFileNumber();
        final List<FileName.FileNameMeta> consumerLogFileMetas =
                FileName.getFileNameMetas(baseDir, fileMeta -> fileMeta.getType() == FileType.ConsumerCommit
                        && fileMeta.getFileNumber() >= fileNumber);

        for (int i = consumerLogFileMetas.size() - 1; i >= 0; --i) {
            final FileName.FileNameMeta fileMeta = consumerLogFileMetas.get(i);
            if (recoverLastConsumerCommittedIdFromLogFile(fileMeta.getFileNumber())) {
                break;
            }
        }
    }

    private synchronized boolean recoverLastConsumerCommittedIdFromLogFile(int fileNumber) throws IOException, StorageException {
        final Path logFilePath = Paths.get(baseDir, FileName.getConsumerCommittedIdFileName(fileNumber));
        if (!Files.exists(logFilePath)) {
            logger.warn("Log file {} was deleted. We can't recover consumer committed id from it.", logFilePath);
            return false;
        }

        final FileChannel ch = FileChannel.open(logFilePath, StandardOpenOption.READ);
        long readEndPosition;
        try (LogReader reader = new LogReader(ch, true)) {
            long id = lastCommittedId;
            while (true) {
                List<byte[]> logOpt = reader.readLog();
                if (!logOpt.isEmpty()) {
                    id = Bits.getLong(compact(logOpt), 0);
                } else {
                    break;
                }
            }

            readEndPosition = ch.position();

            if (id > lastCommittedId) {
                final FileChannel logFile = FileChannel.open(logFilePath, StandardOpenOption.WRITE);
                assert consumerCommitLogWriter == null;
                assert consumerCommitLogFileNumber == 0;
                consumerCommitLogWriter = new LogWriter(logFile, readEndPosition);
                consumerCommitLogFileNumber = fileNumber;
                lastCommittedId = id;
                return true;
            }
        } catch (BadRecordException ex) {
            logger.warn("got \"{}\" record in log file:\"{}\". ", ex.getType(), logFilePath);
        }
        return false;
    }

    private byte[] encodeObjectWithId(ObjectWithId obj) {
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + obj.getObjectInBytes().length);
        buffer.putLong(obj.getId());
        buffer.putInt(obj.getObjectInBytes().length);
        buffer.put(obj.getObjectInBytes());

        return buffer.array();
    }

    private ObjectWithId decodeObjectWithId(List<byte[]> bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(compact(bytes));
        final long id = buffer.getLong();
        final int length = buffer.getInt();
        final byte[] bs = new byte[length];
        buffer.get(bs);

        return new ObjectWithId(id, bs);
    }

    private byte[] compact(List<byte[]> output) {
        final int size = output.stream().mapToInt(b -> b.length).sum();
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] bytes : output) {
            buffer.put(bytes);
        }
        return buffer.array();
    }

    private synchronized List<ObjectWithId> doFetch(long fromId, int limit) throws StorageException {
        Itr itr;

        if (!mm.isEmpty() && fromId >= mm.firstId()) {
            return mm.getEntries(fromId, limit);
        }

        try {
            if (imm != null && fromId >= imm.firstId()) {
                List<SeekableIterator<Long, ObjectWithId>> itrs = Arrays.asList(imm.iterator(), mm.iterator());
                for (SeekableIterator<Long, ObjectWithId> it : itrs) {
                    it.seek(fromId);
                }

                itr = new Itr(itrs);
            } else {
                itr = internalIterator(fromId);
            }

            List<ObjectWithId> ret = new ArrayList<>();
            while (itr.hasNext()) {
                ObjectWithId e = itr.next();
                if (ret.size() >= limit) {
                    break;
                }

                ret.add(e);
            }

            return ret;
        } catch (StorageRuntimeException ex) {
            throw new StorageException(ex.getMessage(), ex.getCause());
        }
    }

    private synchronized boolean makeRoomForEntry(boolean force) {
        try {
            boolean forceRun = force;
            while (true) {
                if (status != StorageStatus.OK) {
                    return false;
                } else if (forceRun || mm.getMemoryUsedInBytes() > Constant.kMaxMemtableSize) {
                    if (imm != null) {
                        this.wait();
                        continue;
                    }

                    forceRun = false;
                    makeRoomForEntry0();
                } else {
                    return true;
                }
            }
        } catch (IOException | InterruptedException t) {
            logger.error("make room for new entry failed", t);
        }
        return false;
    }

    private void makeRoomForEntry0() throws IOException {
        final LogWriter logWriter = createNewDataLogWriter();
        if (dataLogWriter != null) {
            dataLogWriter.close();
        }
        dataLogWriter = logWriter;
        imm = mm;
        mm = new Memtable();
        logger.debug("Trigger compaction, new log file number={}", dataLogFileNumber);
        sstableWriterPool.submit(this::writeMemTable);
    }

    private LogWriter createNewDataLogWriter() throws IOException {
        final int nextLogFileNumber = manifest.getNextFileNumber();
        final String nextLogFile = FileName.getLogFileName(nextLogFileNumber);
        final FileChannel logFile = FileChannel.open(Paths.get(baseDir, nextLogFile),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final LogWriter writer = new LogWriter(logFile);
        dataLogFileNumber = nextLogFileNumber;
        return writer;
    }

    private LogWriter createConsumerCommitLogWriter() throws IOException {
        final int nextLogFileNumber = manifest.getNextFileNumber();
        final String nextLogFile = FileName.getConsumerCommittedIdFileName(nextLogFileNumber);
        final FileChannel logFile = FileChannel.open(Paths.get(baseDir, nextLogFile),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final LogWriter writer = new LogWriter(logFile);
        consumerCommitLogFileNumber = nextLogFileNumber;
        return writer;
    }

    private void writeMemTable() {
        logger.debug("start write mem table in background");
        StorageStatus status = StorageStatus.OK;
        try {
            ManifestRecord record = null;
            assert imm != null;
            if (!imm.isEmpty()) {
                assert imm.firstId() > manifest.getLastId();
                final SSTableFileMetaInfo meta = writeMemTableToSSTable(imm);
                record = ManifestRecord.newPlainRecord();
                record.addMeta(meta);
                record.setConsumerCommitLogFileNumber(consumerCommitLogFileNumber);
                record.setDataLogFileNumber(dataLogFileNumber);
                manifest.logRecord(record);
            }

            final Set<Integer> remainMetasFileNumberSet = manifest.searchMetas(Long.MIN_VALUE)
                    .stream()
                    .map(SSTableFileMetaInfo::getFileNumber)
                    .collect(Collectors.toSet());
            for (Integer fileNumber : tableCache.getAllFileNumbers()) {
                if (!remainMetasFileNumberSet.contains(fileNumber)) {
                    tableCache.evict(fileNumber);
                }
            }

            FileName.deleteOutdatedFiles(baseDir, dataLogFileNumber, consumerCommitLogFileNumber, tableCache);

            logger.debug("write mem table in background done with manifest record {}", record);
        } catch (Throwable t) {
            logger.error("write memtable in background failed", t);
            status = StorageStatus.ERROR;
        } finally {
            synchronized (this) {
                if (status == StorageStatus.OK) {
                    imm = null;
                } else {
                    this.status = status;
                }
                this.notifyAll();
            }
        }
    }

    private SSTableFileMetaInfo writeMemTableToSSTable(Memtable mm) throws IOException {
        final SSTableFileMetaInfo meta = new SSTableFileMetaInfo();

        final int fileNumber = manifest.getNextFileNumber();
        meta.setFileNumber(fileNumber);
        meta.setFirstId(mm.firstId());
        meta.setLastId(mm.lastId());

        final String tableFileName = FileName.getSSTableName(fileNumber);
        final Path tableFile = Paths.get(baseDir, tableFileName);
        try (FileChannel ch = FileChannel.open(tableFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            final TableBuilder tableBuilder = new TableBuilder(ch);

            for (ObjectWithId entry : mm) {
                final byte[] data = entry.getObjectInBytes();
                tableBuilder.add(entry.getId(), data);
            }

            long tableFileSize = tableBuilder.finishBuild();

            if (tableFileSize > 0) {
                meta.setFileSize(tableFileSize);

                ch.force(true);
            }
        }

        tableCache.loadTable(fileNumber, meta.getFileSize());

        if (meta.getFileSize() <= 0) {
            Files.deleteIfExists(tableFile);
        }

        return meta;
    }

    private Itr internalIterator(long start) throws StorageException {
        List<SeekableIterator<Long, ObjectWithId>> itrs = getSSTableIterators(start);
        if (imm != null) {
            itrs.add(imm.iterator().seek(start));
        }
        itrs.add(mm.iterator().seek(start));
        for (SeekableIterator<Long, ObjectWithId> itr : itrs) {
            itr.seek(start);
            if (itr.hasNext()) {
                break;
            }
        }

        return new Itr(itrs);
    }

    private List<SeekableIterator<Long, ObjectWithId>> getSSTableIterators(long start) throws StorageException {
        try {
            List<SSTableFileMetaInfo> metas = manifest.searchMetas(start);
            List<SeekableIterator<Long, ObjectWithId>> ret = new ArrayList<>(metas.size());
            for (SSTableFileMetaInfo meta : metas) {
                ret.add(tableCache.iterator(meta.getFileNumber(), meta.getFileSize()));
            }
            return ret;
        } catch (IOException ex) {
            throw new StorageException(
                    String.format("get sstable iterators start: %s from SSTable failed", start), ex);
        }
    }

    private synchronized void tryTruncate() {
        try {
            final long lastCommittedId = getLastCommittedId();
            final long truncateId = Math.max(0, lastCommittedId);
            manifest.truncateToId(truncateId);
            lastTryTruncateTime = System.nanoTime();
        } catch (Exception ex) {
            logger.error("Truncate handler failed for entry", ex);
        }
    }

    private static class Itr implements Iterator<ObjectWithId> {
        private final List<SeekableIterator<Long, ObjectWithId>> iterators;
        private int lastItrIndex;

        Itr(List<SeekableIterator<Long, ObjectWithId>> iterators) {
            this.iterators = iterators;
        }

        @Override
        public boolean hasNext() {
            for (int i = lastItrIndex; i < iterators.size(); i++) {
                SeekableIterator<Long, ObjectWithId> itr = iterators.get(i);
                if (itr.hasNext()) {
                    lastItrIndex = i;
                    return true;
                }
            }

            lastItrIndex = iterators.size();
            return false;
        }

        @Override
        public ObjectWithId next() {
            assert lastItrIndex >= 0 && lastItrIndex < iterators.size();
            return iterators.get(lastItrIndex).next();
        }
    }
}
