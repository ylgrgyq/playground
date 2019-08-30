package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

final class Manifest {
    private static final Logger logger = LoggerFactory.getLogger(Manifest.class.getName());

    private final String baseDir;
    private final List<SSTableFileMetaInfo> metas;
    private final ReentrantLock metasLock;

    @Nullable
    private LogWriter manifestRecordWriter;
    private int nextFileNumber = 1;
    private int dataLogFileNumber;
    private int manifestFileNumber;

    Manifest(String baseDir) {
        this.baseDir = baseDir;
        this.metas = new CopyOnWriteArrayList<>();
        this.metasLock = new ReentrantLock();
    }

    private void registerMetas(ManifestRecord record) {
        metasLock.lock();
        try {
            metas.addAll(record.getMetas());
        } finally {
            metasLock.unlock();
        }
    }

    synchronized void logRecord(ManifestRecord record) throws IOException {
        assert record.getDataLogFileNumber() >= dataLogFileNumber;

        registerMetas(record);

        String manifestFileName = null;
        if (manifestRecordWriter == null) {
            final int fileNumber = getNextFileNumber();
            manifestFileNumber = fileNumber;
            manifestFileName = FileName.getManifestFileName(fileNumber);
            final FileChannel manifestFile = FileChannel.open(Paths.get(baseDir, manifestFileName),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            manifestRecordWriter = new LogWriter(manifestFile);
        }

        record.setNextFileNumber(nextFileNumber);
        manifestRecordWriter.append(record.encode());
        manifestRecordWriter.flush();

        logger.debug("written manifest record {} to manifest file number {}", record, manifestFileNumber);

        // only set CURRENT to the new manifest file after a new record has safely written to it
        if (manifestFileName != null) {
            FileName.setCurrentFile(baseDir, manifestFileNumber);
        }
    }

    synchronized void recover(String manifestFileName) throws IOException, StorageException {
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
                    nextFileNumber = record.getNextFileNumber();
                    dataLogFileNumber = record.getDataLogFileNumber();
                    ms.addAll(record.getMetas());
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
                return metas.get(0).getFirstId();
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
                return metas.get(metas.size() - 1).getLastId();
            } else {
                return -1L;
            }
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

    synchronized int getDataLogFileNumber() {
        return dataLogFileNumber;
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
                    .filter(meta -> meta.getFirstId() < endKey)
                    .collect(Collectors.toList());
        } finally {
            metasLock.unlock();
        }
    }

    private int traverseSearchStartMeta(long index) {
        int i = 0;
        while (i < metas.size()) {
            SSTableFileMetaInfo meta = metas.get(i);
            if (index <= meta.getFirstId()) {
                break;
            } else if (index <= meta.getLastId()) {
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
            if (index >= meta.getFirstId() && index <= meta.getLastId()) {
                return mid;
            } else if (index < meta.getFirstId()) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        return start;
    }
}
