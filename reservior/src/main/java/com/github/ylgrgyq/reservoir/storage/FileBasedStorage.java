package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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

    private FileChannel storageLockChannel;
    private FileLock storageLock;
    private LogWriter logWriter;
    private volatile int logFileNumber;
    private long firstIndexInStorage;
    private long lastIndexInStorage;
    private Memtable mm;
    private volatile Memtable imm;
    private volatile StorageStatus status;
    private boolean backgroundWriteSstableRunning;
    private volatile CountDownLatch shutdownLatch;

    public FileBasedStorage(String storageBaseDir) {
        requireNonNull(storageBaseDir, "storageBaseDir");
        Path baseDirPath = Paths.get(storageBaseDir);

        if (Files.exists(baseDirPath) && !Files.isDirectory(baseDirPath)) {
            throw new IllegalArgumentException("\"" + storageBaseDir + "\" must be a directory");
        }

        this.mm = new Memtable();
        this.baseDir = storageBaseDir;
        this.firstIndexInStorage = -1;
        this.lastIndexInStorage = -1;
        this.status = StorageStatus.NEED_INIT;
        this.tableCache = new TableCache(baseDir);
        this.manifest = new Manifest(baseDir);
        this.backgroundWriteSstableRunning = false;
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

        assert mm.isEmpty() || mm.lastKey() == lastIndexInStorage :
                String.format("actual lastIndex:%s lastIndexInMm:%s", lastIndexInStorage, mm.lastKey());
        return lastIndexInStorage;
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

        if (firstIndexInStorage < 0) {
            ObjectWithId first = batch.get(0);
            firstIndexInStorage = first.getId();
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
                    logWriter.append(data);
                    mm.add(e.getId(), e);
                    lastIndexInStorage = e.getId();
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

                        if (logWriter != null) {
                            logWriter.close();
                        }

                        manifest.close();

                        tableCache.evictAll();

                        if (storageLock != null && storageLock.isValid()) {
                            storageLock.release();
                        }

                        if (storageLockChannel != null && storageLockChannel.isOpen()) {
                            storageLockChannel.close();
                        }
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

    //    @Override
    public synchronized void init() {
        if (status != StorageStatus.NEED_INIT) {
            throw new IllegalArgumentException("status: " + status + " (expect: " + StorageStatus.NEED_INIT + " )");
        }

        try {
            createStorageDir();

            Path lockFilePath = Paths.get(baseDir, FileName.getLockFileName());
            storageLockChannel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            storageLock = storageLockChannel.tryLock();
            if (storageLock == null) {
                throw new IllegalStateException("failed to lock file: " + baseDir);
            }

            logger.debug("start init storage under {}", baseDir);

            ManifestRecord record = ManifestRecord.newPlainRecord();
            if (Files.exists(Paths.get(baseDir, FileName.getCurrentManifestFileName()))) {
                recoverStorage(record);
            }

            if (logWriter == null) {
                int nextLogFileNumber = manifest.getNextFileNumber();
                String nextLogFile = FileName.getLogFileName(nextLogFileNumber);
                FileChannel logFile = FileChannel.open(Paths.get(baseDir, nextLogFile),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                logWriter = new LogWriter(logFile);
                logFileNumber = nextLogFileNumber;
            }

            record.setLogNumber(logFileNumber);
            manifest.logRecord(record);

            firstIndexInStorage = manifest.getFirstIndex();
            if (firstIndexInStorage < 0) {
                firstIndexInStorage = mm.firstKey();
            }

            lastIndexInStorage = mm.lastKey();
            if (lastIndexInStorage < 0) {
                lastIndexInStorage = manifest.getLastIndex();
            }
            status = StorageStatus.OK;
        } catch (IOException | StorageException t) {
            throw new IllegalStateException("init storage failed", t);
        } finally {
            if (status != StorageStatus.OK) {
                status = StorageStatus.ERROR;
                if (storageLockChannel != null) {
                    try {
                        if (storageLock != null) {
                            storageLock.release();
                        }
                        storageLockChannel.close();
                    } catch (IOException ex) {
                        logger.error("storage release lock failed", ex);
                    }
                }
            }
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

    private void recoverStorage(ManifestRecord record) throws IOException, StorageException {
        Path currentFilePath = Paths.get(baseDir, FileName.getCurrentManifestFileName());
        assert Files.exists(currentFilePath);
        String currentManifestFileName = new String(Files.readAllBytes(currentFilePath), StandardCharsets.UTF_8);
        assert !currentManifestFileName.isEmpty();

        manifest.recover(currentManifestFileName);

        int logFileNumber = manifest.getLogFileNumber();
        File baseDirFile = new File(baseDir);
        File[] files = baseDirFile.listFiles();
        List<FileName.FileNameMeta> logsFileMetas = Collections.emptyList();
        if (files != null) {
            logsFileMetas = Arrays.stream(files)
                    .filter(File::isFile)
                    .map(File::getName)
                    .map(FileName::parseFileName)
                    .filter(meta -> meta.getType() == FileName.FileType.Log && meta.getFileNumber() >= logFileNumber)
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < logsFileMetas.size(); ++i) {
            FileName.FileNameMeta fileMeta = logsFileMetas.get(i);
            recoverMmFromLogFiles(fileMeta.getFileNumber(), record, i == logsFileMetas.size() - 1);
        }
    }

    private void recoverMmFromLogFiles(int fileNumber, ManifestRecord record, boolean lastLogFile) throws IOException, StorageException {
        Path logFilePath = Paths.get(baseDir, FileName.getLogFileName(fileNumber));
        if (Files.exists(logFilePath)) {
            throw new IllegalArgumentException("log file " + logFilePath + " was deleted");
        }

        long readEndPosition;
        boolean noNewSSTable = true;
        Memtable mm = null;
        FileChannel ch = FileChannel.open(logFilePath, StandardOpenOption.READ);
        try (LogReader reader = new LogReader(ch, 0, true)) {
            while (true) {
                List<byte[]> logOpt = reader.readLog();
                if (!logOpt.isEmpty()) {
//                    LogEntry e = LogEntry.parseFrom(logOpt.get());
                    if (mm == null) {
                        mm = new Memtable();
                    }
//                    mm.add(e.getIndex(), e);
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
                FileChannel logFile = FileChannel.open(logFilePath, StandardOpenOption.WRITE);
                assert logWriter == null;
                assert logFileNumber == 0;
                logWriter = new LogWriter(logFile, readEndPosition);
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
            SSTableFileMetaInfo meta = writeMemTableToSSTable(mm);
            record.addMeta(meta);
        }
    }

    //    @Override
    public synchronized long getFirstIndex() {
//        checkArgument(status == StorageStatus.OK,
//                "FileBasedStorage's status is not normal, currently: %s", status);
        return firstIndexInStorage;
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
        int nextLogFileNumber = manifest.getNextFileNumber();
        String nextLogFile = FileName.getLogFileName(nextLogFileNumber);
        FileChannel logFile = FileChannel.open(Paths.get(baseDir, nextLogFile),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        if (logWriter != null) {
            logWriter.close();
        }
        logWriter = new LogWriter(logFile);
        logFileNumber = nextLogFileNumber;
        imm = mm;
        mm = new Memtable();
        backgroundWriteSstableRunning = true;
        logger.debug("trigger compaction, new log file number={}", logFileNumber);
        sstableWriterPool.submit(this::writeMemTable);
    }

    private void writeMemTable() {
        logger.debug("start write mem table in background");
        StorageStatus status = StorageStatus.OK;
        try {
            ManifestRecord record = null;
            if (!imm.isEmpty()) {
                if (imm.firstKey() > manifest.getLastIndex()) {
                    SSTableFileMetaInfo meta = writeMemTableToSSTable(imm);
                    record = ManifestRecord.newPlainRecord();
                    record.addMeta(meta);
                    record.setLogNumber(logFileNumber);
                } else {
                    List<SSTableFileMetaInfo> remainMetas = new ArrayList<>();

                    long firstKeyInImm = imm.firstKey();
                    List<SSTableFileMetaInfo> allMetas = manifest.searchMetas(Long.MIN_VALUE, Long.MAX_VALUE);
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
                firstIndexInStorage = manifest.getFirstIndex();
            }

            Set<Integer> remainMetasFileNumberSet = manifest.searchMetas(Long.MIN_VALUE, Long.MAX_VALUE)
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
