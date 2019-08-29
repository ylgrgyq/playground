package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class FileBasedStorage implements ProducerStorage, ConsumerStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedStorage.class.getName());

    private final ExecutorService sstableWriterPool = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("SSTable-Writer-"));
    private final String baseDir;
    private final TableCache tableCache;
    private final Manifest manifest;

    private FileLock storageLock;
    private LogWriter consumerCommitLogWriter;
    private LogWriter dataLogWriter;
    private volatile int logFileNumber;
    private long firstIdInStorage;
    private long lastIdInStorage;
    private Memtable mm;
    private volatile Memtable imm;
    private volatile StorageStatus status;
    private boolean backgroundWriteSstableRunning;
    private volatile CountDownLatch shutdownLatch;

    public FileBasedStorage(String storageBaseDir) throws StorageException{
        requireNonNull(storageBaseDir, "storageBaseDir");
        Path baseDirPath = Paths.get(storageBaseDir);

        if (Files.exists(baseDirPath) && !Files.isDirectory(baseDirPath)) {
            throw new IllegalArgumentException("\"" + storageBaseDir + "\" must be a directory");
        }

        this.mm = new Memtable();
        this.baseDir = storageBaseDir;
        this.firstIdInStorage = -1;
        this.lastIdInStorage = -1;
        this.status = StorageStatus.NEED_INIT;
        this.tableCache = new TableCache(baseDir);
        this.manifest = new Manifest(baseDir);
        this.backgroundWriteSstableRunning = false;

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

            record.setLogNumber(this.logFileNumber);
            this.manifest.logRecord(record);

            firstIdInStorage = manifest.getFirstId();
            if (firstIdInStorage < 0) {
                firstIdInStorage = mm.firstKey();
            }

            lastIdInStorage = mm.lastKey();
            if (lastIdInStorage < 0) {
                lastIdInStorage = manifest.getLastId();
            }
            status = StorageStatus.OK;
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
    public void commitId(long id) throws StorageException {

    }

    @Override
    public long getLastCommittedId() throws StorageException {
        return 0;
    }

    @Override
    public List<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException {
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        if (fromId < getFirstIndex()) {
            throw new StorageException("log compacted exception");
        }

        if (!mm.isEmpty() && fromId >= mm.firstKey()) {
            return mm.getEntries(fromId, limit);
        }

        Itr itr;
        if (imm != null && fromId >= imm.firstKey()) {
            List<SeekableIterator<Long, ObjectWithId>> itrs = Arrays.asList(imm.iterator(), mm.iterator());
            for (SeekableIterator<Long, ObjectWithId> it : itrs) {
                it.seek(fromId);
            }

            itr = new Itr(itrs);
        } else {
            itr = internalIterator(fromId, limit);
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
    }

    @Override
    public List<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException {

        return null;
    }

    @Override
    public long getLastProducedId() throws StorageException {
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        assert mm.isEmpty() || mm.lastKey() == lastIdInStorage :
                String.format("actual lastIndex:%s lastIndexInMm:%s", lastIdInStorage, mm.lastKey());
        return lastIdInStorage;
    }

    @Override
    public void store(List<ObjectWithId> batch) throws StorageException {
        requireNonNull(batch, "batch");
        if (status != StorageStatus.OK) {
            throw new IllegalStateException("FileBasedStorage's status is not normal, currently: " + status);
        }

        if (batch.isEmpty()) {
            logger.warn("append with empty entries");
            return;
        }

        if (firstIdInStorage < 0) {
            ObjectWithId first = batch.get(0);
            firstIdInStorage = first.getId();
        }

        try {
            long lastIndex = -1;
            for (ObjectWithId e : batch) {
                if (e.getId() <= lastIndex) {
                    throw new IllegalStateException("log entries being appended is not monotone increasing: " + batch);
                }

                lastIndex = e.getId();
                if (makeRoomForEntry(false)) {
                    byte[] data = e.getObjectInBytes();
                    dataLogWriter.append(data);
                    mm.add(e.getId(), e);
                    lastIdInStorage = e.getId();
                } else {
                    throw new StorageException("make room on file based storage failed");
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
            shutdownLatch = new CountDownLatch(1);

            sstableWriterPool.submit(() -> {
                synchronized (FileBasedStorage.this) {
                    try {
                        logger.debug("shutting file based storage down");
                        sstableWriterPool.shutdownNow();

                        if (dataLogWriter != null) {
                            dataLogWriter.close();
                        }

                        manifest.close();

                        tableCache.evictAll();

                        releaseStorageLock();
                        logger.debug("file based storage shutdown successfully");
                    } catch (Exception ex) {
                        logger.error("shutdown failed", ex);
                    } finally {
                        shutdownLatch.countDown();
                    }
                }
            });
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

    private void recoverStorage(Path currentFilePath, ManifestRecord record) throws IOException, StorageException {
        assert Files.exists(currentFilePath);
        final String currentManifestFileName = new String(Files.readAllBytes(currentFilePath), StandardCharsets.UTF_8);
        if (currentManifestFileName.isEmpty()) {
            throw new StorageException("empty CURRENT file in storage dir: " + baseDir);
        }

        manifest.recover(currentManifestFileName);

        final int logFileNumber = manifest.getLogFileNumber();
        final File baseDirFile = new File(baseDir);
        final File[] files = baseDirFile.listFiles();
        List<FileName.FileNameMeta> logsFileMetas = Collections.emptyList();
        if (files != null) {
            logsFileMetas = Arrays.stream(files)
                    .filter(File::isFile)
                    .map(File::getName)
                    .map(FileName::parseFileName)
                    .filter(fileMeta -> fileMeta.getType() == FileName.FileType.Log && fileMeta.getFileNumber() >= logFileNumber)
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < logsFileMetas.size(); ++i) {
            final FileName.FileNameMeta fileMeta = logsFileMetas.get(i);
            recoverMemtableFromLogFiles(fileMeta.getFileNumber(), record, i == logsFileMetas.size() - 1);
        }
    }

    private void recoverMemtableFromLogFiles(int fileNumber, ManifestRecord record, boolean lastLogFile) throws IOException, StorageException {
        final Path logFilePath = Paths.get(baseDir, FileName.getLogFileName(fileNumber));
        if (Files.exists(logFilePath)) {
            logger.warn("Log file {} was deleted. We can't recover memtable from it.", logFilePath);
            return;
        }

        final FileChannel ch = FileChannel.open(logFilePath, StandardOpenOption.READ);
        long readEndPosition;
        boolean noNewSSTable = true;
        Memtable mm = null;
        try (LogReader reader = new LogReader(ch, true)) {
            while (true) {
                List<byte[]> logOpt = reader.readLog();
                if (!logOpt.isEmpty()) {
                    ObjectWithId e = new ObjectWithId(logOpt);
                    if (mm == null) {
                        mm = new Memtable();
                    }
                    mm.add(e.getId(), e);
                    if (mm.getMemoryUsedInBytes() > Constant.kMaxMemtableSize) {
                        SSTableFileMetaInfo meta = writeMemTableToSSTable(mm);
                        record.addMeta(meta);
                        noNewSSTable = false;
                        mm = null;
                    }
                } else {
                    break;
                }
            }

            readEndPosition = ch.position();

            if (lastLogFile && noNewSSTable) {
                final FileChannel logFile = FileChannel.open(logFilePath, StandardOpenOption.WRITE);
                assert dataLogWriter == null;
                assert logFileNumber == 0;
                dataLogWriter = new LogWriter(logFile, readEndPosition);
                logFileNumber = fileNumber;
                if (mm != null) {
                    this.mm = mm;
                    mm = null;
                }
            }
        } catch (BadRecordException ex) {
            logger.warn("got \"{}\" record in log file:\"{}\". ", ex.getType(), logFilePath);
        }

        if (mm != null) {
            final SSTableFileMetaInfo meta = writeMemTableToSSTable(mm);
            record.addMeta(meta);
        }
    }

    private synchronized long getFirstIndex() {
        return firstIdInStorage;
    }

    private boolean makeRoomForEntry(boolean force) {
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
        backgroundWriteSstableRunning = true;
        logger.debug("Trigger compaction, new log file number={}", logFileNumber);
        sstableWriterPool.submit(this::writeMemTable);
    }

    private LogWriter createNewDataLogWriter() throws IOException {
        final int nextLogFileNumber = manifest.getNextFileNumber();
        final String nextLogFile = FileName.getLogFileName(nextLogFileNumber);
        final FileChannel logFile = FileChannel.open(Paths.get(baseDir, nextLogFile),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final LogWriter writer = new LogWriter(logFile);
        logFileNumber = nextLogFileNumber;
        return writer;
    }

    private void writeMemTable() {
        logger.debug("start write mem table in background");
        StorageStatus status = StorageStatus.OK;
        try {
            ManifestRecord record = null;
            if (!imm.isEmpty()) {
                if (imm.firstKey() > manifest.getLastId()) {
                    final SSTableFileMetaInfo meta = writeMemTableToSSTable(imm);
                    record = ManifestRecord.newPlainRecord();
                    record.addMeta(meta);
                    record.setLogNumber(logFileNumber);
                } else {
                    final List<SSTableFileMetaInfo> remainMetas = new ArrayList<>();

                    final long firstKeyInImm = imm.firstKey();
                    final List<SSTableFileMetaInfo> allMetas = manifest.searchMetas(Long.MIN_VALUE, Long.MAX_VALUE);
                    for (SSTableFileMetaInfo meta : allMetas) {
                        if (firstKeyInImm < meta.getLastKey()) {
                            remainMetas.add(mergeMemTableAndSStable(meta, imm));
                            break;
                        } else {
                            remainMetas.add(meta);
                        }
                    }

                    assert !remainMetas.isEmpty();
                    record = ManifestRecord.newReplaceAllExistedMetasRecord();
                    record.addMetas(remainMetas);
                    record.setLogNumber(logFileNumber);
                }
                manifest.logRecord(record);
            }

            if (manifest.processCompactTask()) {
                firstIdInStorage = manifest.getFirstId();
            }

            final Set<Integer> remainMetasFileNumberSet = manifest.searchMetas(Long.MIN_VALUE, Long.MAX_VALUE)
                    .stream()
                    .map(SSTableFileMetaInfo::getFileNumber)
                    .collect(Collectors.toSet());
            for (Integer fileNumber : tableCache.getAllFileNumbers()) {
                if (!remainMetasFileNumberSet.contains(fileNumber)) {
                    tableCache.evict(fileNumber);
                }
            }

            FileName.deleteOutdatedFiles(baseDir, logFileNumber, tableCache);

            logger.debug("write mem table in background done with manifest record {}", record);
        } catch (Throwable t) {
            logger.error("write memtable in background failed", t);
            status = StorageStatus.ERROR;
        } finally {
            synchronized (this) {
                backgroundWriteSstableRunning = false;
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
        return mergeMemTableAndSStable(null, mm);
    }

    private SSTableFileMetaInfo mergeMemTableAndSStable(SSTableFileMetaInfo sstable, Memtable mm) throws IOException {
        assert mm != null;

        SSTableFileMetaInfo meta = new SSTableFileMetaInfo();

        int fileNumber = manifest.getNextFileNumber();
        meta.setFileNumber(fileNumber);
        meta.setFirstKey(Math.min(mm.firstKey(), sstable != null ? sstable.getFirstKey() : Long.MAX_VALUE));
        meta.setLastKey(Math.max(mm.lastKey(), sstable != null ? sstable.getLastKey() : -1));

        String tableFileName = FileName.getSSTableName(fileNumber);
        Path tableFile = Paths.get(baseDir, tableFileName);
        try (FileChannel ch = FileChannel.open(tableFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            TableBuilder tableBuilder = new TableBuilder(ch);

            Iterator<ObjectWithId> ssTableIterator = Collections.emptyIterator();
            if (sstable != null) {
                ssTableIterator = tableCache.iterator(sstable.getFileNumber(), sstable.getFileSize());
            }

            long boundary = mm.firstKey();
            while (ssTableIterator.hasNext()) {
                ObjectWithId entry = ssTableIterator.next();
                if (entry.getId() < boundary) {
                    byte[] data = entry.getObjectInBytes();
                    tableBuilder.add(entry.getId(), data);
                } else {
                    break;
                }
            }

            for (ObjectWithId entry : mm) {
                byte[] data = entry.getObjectInBytes();
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

    public Future<Long> compact(long toIndex) {
//        checkArgument(toIndex > 0);

        return manifest.compact(toIndex);
    }

    synchronized void forceFlushMemtable() {
        makeRoomForEntry(true);
    }

    synchronized void waitWriteSstableFinish() throws InterruptedException {
        if (backgroundWriteSstableRunning) {
            this.wait();
        }
    }

    private Itr internalIterator(long start, long end) throws StorageException {
        List<SeekableIterator<Long, ObjectWithId>> itrs = getSSTableIterators(start, end);
        if (imm != null) {
            itrs.add(imm.iterator());
        }
        itrs.add(mm.iterator());
        for (SeekableIterator<Long, ObjectWithId> itr : itrs) {
            itr.seek(start);
            if (itr.hasNext()) {
                break;
            }
        }

        return new Itr(itrs);
    }

    private List<SeekableIterator<Long, ObjectWithId>> getSSTableIterators(long start, long end) throws StorageException {
        try {
            List<SSTableFileMetaInfo> metas = manifest.searchMetas(start, end);
            List<SeekableIterator<Long, ObjectWithId>> ret = new ArrayList<>(metas.size());
            for (SSTableFileMetaInfo meta : metas) {
                ret.add(tableCache.iterator(meta.getFileNumber(), meta.getFileSize()));
            }
            return ret;
        } catch (IOException ex) {
            throw new StorageException(
                    String.format("get sstable iterators start:%s end:%s from SSTable failed", start, end), ex);
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
