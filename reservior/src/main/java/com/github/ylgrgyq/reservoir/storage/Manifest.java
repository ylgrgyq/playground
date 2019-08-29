package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Author: ylgrgyq
 * Date: 18/6/10
 */
class Manifest {
    private static final Logger logger = LoggerFactory.getLogger(Manifest.class.getName());

    private final BlockingQueue<CompactTask<Long>> compactTaskQueue;
    private final String baseDir;
    private final List<SSTableFileMetaInfo> metas;
    private final ReentrantLock metasLock;

    private int nextFileNumber = 1;
    private int logNumber;
    private LogWriter manifestRecordWriter;
    private int manifestFileNumber;

    Manifest(String baseDir) {
        this.baseDir = baseDir;
        this.metas = new CopyOnWriteArrayList<>();
        this.compactTaskQueue = new LinkedBlockingQueue<>();
        this.metasLock = new ReentrantLock();
    }

    private void registerMetas(ManifestRecord record) {
        metasLock.lock();
        try {
            if (record.getType() == ManifestRecord.Type.REPLACE_METAS) {
                this.metas.clear();
            }
            this.metas.addAll(record.getMetas());
        } finally {
            metasLock.unlock();
        }
    }

    synchronized void logRecord(ManifestRecord record) throws IOException {
        assert record.getType() != ManifestRecord.Type.PLAIN || record.getLogNumber() >= logNumber;

        registerMetas(record);

        String manifestFileName = null;
        if (manifestRecordWriter == null) {
            manifestFileNumber = getNextFileNumber();
            manifestFileName = FileName.getManifestFileName(manifestFileNumber);
            FileChannel manifestFile = FileChannel.open(Paths.get(baseDir, manifestFileName),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            manifestRecordWriter = new LogWriter(manifestFile);
        }

        if (record.getType() == ManifestRecord.Type.PLAIN) {
            record.setNextFileNumber(nextFileNumber);
        }
        manifestRecordWriter.append(record.encode());
        manifestRecordWriter.flush();

        logger.debug("written manifest record {} to manifest file number {}", record, manifestFileNumber);

        if (manifestFileName != null) {
            FileName.setCurrentFile(baseDir, manifestFileNumber);
        }
    }

    boolean processCompactTask() throws IOException {
        long greatestToKey = -1L;
        List<CompactTask<Long>> tasks = new ArrayList<>();
        CompactTask<Long> task;
        while ((task = compactTaskQueue.poll()) != null) {
            tasks.add(task);
            if (task.getToKey() > greatestToKey) {
                greatestToKey = task.getToKey();
            }
        }

        if (greatestToKey > 0) {
            List<SSTableFileMetaInfo> remainMetas = searchMetas(greatestToKey, Integer.MAX_VALUE);
            if (remainMetas.size() == 0) {
                // we don't have enough meta tables to fulfill this compact.
                // so we add compact tasks back to queue and wait for next flush SSTable time
                compactTaskQueue.addAll(tasks);
            } else {
                if (remainMetas.size() < metas.size()) {
                    ManifestRecord record = ManifestRecord.newReplaceAllExistedMetasRecord();
                    record.addMetas(remainMetas);
                    logRecord(record);

                    registerMetas(record);
                } else {
                    assert remainMetas.size() == metas.size();
                }

                // TODO complete compact task with requesting toIndex in CompactTask
                final Long firstIndex = getFirstId();
                tasks.forEach(t -> t.getFuture().complete(firstIndex));
                return true;
            }
        }

        return false;
    }

    CompletableFuture<Long> compact(long toKey) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        CompactTask<Long> task = new CompactTask<>(future, toKey);
        compactTaskQueue.add(task);
        return future;
    }

    private void recover(String manifestFileName) throws IOException, StorageException {
        final Path manifestFilePath = Paths.get(baseDir, manifestFileName);
        if (!Files.exists(manifestFilePath)) {
            throw new StorageException("CURRENT file points to an non-exists manifest file: " +
                    baseDir + File.separator + manifestFileName);
        }

        final FileChannel manifestFile = FileChannel.open(manifestFilePath, StandardOpenOption.READ);
        try (final LogReader reader = new LogReader(manifestFile, true)) {
            List<SSTableFileMetaInfo> ms = new ArrayList<>();
            while (true) {
                final List<byte[]> logOpt = reader.readLog();
                if (!logOpt.isEmpty()) {
                    final ManifestRecord record = ManifestRecord.decode(logOpt);
                    switch (record.getType()) {
                        case PLAIN:
                            nextFileNumber = record.getNextFileNumber();
                            logNumber = record.getLogNumber();
                            ms.addAll(record.getMetas());
                            break;
                        case REPLACE_METAS:
                            // replace all previous metas with the metas in this manifest record
                            ms = new ArrayList<>(record.getMetas());
                            break;
                        default:
                            throw new StorageException("unknown manifest record type: " + record.getType());
                    }
                } else {
                    break;
                }
            }

            metasLock.lock();
            try {
                // we must make sure that searchMetas will only be called after recovery
                assert metas.isEmpty();
                metas.addAll(ms);
            } finally {
                metasLock.unlock();
            }
        } catch (BadRecordException ex) {
            String msg = String.format("recover manifest from file:\"%s\" failed due to \"%s\" log record",
                    manifestFileName, manifestFilePath);
            throw new StorageException(msg);
        }
    }

    long getFirstId() {
        metasLock.lock();
        try {
            if (!metas.isEmpty()) {
                return metas.get(0).getFirstKey();
            } else {
                return -1L;
            }
        } finally {
            metasLock.unlock();
        }
    }

    long getLastId() {
        metasLock.lock();
        try {
            if (!metas.isEmpty()) {
                return metas.get(metas.size() - 1).getLastKey();
            } else {
                return -1L;
            }
        } finally {
            metasLock.unlock();
        }
    }

    int getLowestSSTableFileNumber() {
        metasLock.lock();
        try {
            return metas.stream()
                    .mapToInt(SSTableFileMetaInfo::getFileNumber)
                    .min()
                    .orElse(-1);
        } finally {
            metasLock.unlock();
        }
    }

    synchronized void close() throws IOException {
        if (manifestRecordWriter != null) {
            manifestRecordWriter.close();
        }
    }

    synchronized int getNextFileNumber() {
        return nextFileNumber++;
    }

    synchronized int getLogFileNumber() {
        return logNumber;
    }

    /**
     * find all the SSTableFileMetaInfo who's index range intersect with startIndex and endIndex
     *
     * @param startKey target start key (inclusive)
     * @param endKey   target end key (exclusive)
     * @return iterator for found SSTableFileMetaInfo
     */
    List<SSTableFileMetaInfo> searchMetas(long startKey, long endKey) {
        metasLock.lock();
        try {
            int startMetaIndex;
            if (metas.size() > 32) {
                startMetaIndex = binarySearchStartMeta(startKey);
            } else {
                startMetaIndex = traverseSearchStartMeta(startKey);
            }

            return metas.subList(startMetaIndex, metas.size())
                    .stream()
                    .filter(meta -> meta.getFirstKey() < endKey)
                    .collect(Collectors.toList());
        } finally {
            metasLock.unlock();
        }
    }

    private int traverseSearchStartMeta(long index) {
        int i = 0;
        while (i < metas.size()) {
            SSTableFileMetaInfo meta = metas.get(i);
            if (index <= meta.getFirstKey()) {
                break;
            } else if (index <= meta.getLastKey()) {
                break;
            }
            ++i;
        }

        return i;
    }

    private int binarySearchStartMeta(long index) {
        int start = 0;
        int end = metas.size();

        while (start < end) {
            int mid = (start + end) / 2;
            SSTableFileMetaInfo meta = metas.get(mid);
            if (index >= meta.getFirstKey() && index <= meta.getLastKey()) {
                return mid;
            } else if (index < meta.getFirstKey()) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        return start;
    }

    private static class CompactTask<T> {
        private final CompletableFuture<T> future;
        private final long toKey;

        CompactTask(CompletableFuture<T> future, long toKey) {
            this.future = future;
            this.toKey = toKey;
        }

        CompletableFuture<T> getFuture() {
            return future;
        }

        long getToKey() {
            return toKey;
        }
    }
}
