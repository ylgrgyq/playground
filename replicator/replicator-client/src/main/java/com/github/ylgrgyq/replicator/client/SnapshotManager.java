package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnapshotManager {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);

    private static final String SNAPSHOT_FILE_PREFIX = "replicator_snapshot_";

    private final Path storagePath;
    private final ReplicatorClientOptions options;
    private volatile Snapshot lastSnapshot;
    private AtomicBoolean purgingOldSnapshots;

    public SnapshotManager(ReplicatorClientOptions options) throws IOException {
        this.options = options;
        this.storagePath = options.getSnapshotStoragePath();
        this.purgingOldSnapshots = new AtomicBoolean(false);

        if (storagePath != null) {
            final File dir = this.storagePath.toFile();
            if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Invalid snapshot storage path, it's a regular file: " + this.storagePath);
            }

            FileUtils.forceMkdir(dir);
            purgeSnapshots();
            loadLastSnapshot();
        } else {
            logger.warn("No snapshot storage path provided, snapshot will not be saved persistently");
        }
    }

    public void storeSnapshot(Snapshot snapshot) throws IOException {
        if (storagePath == null || options.getMaxSnapshotsToKeep() == 0) {
            return;
        }

        Snapshot last = lastSnapshot;
        if (last != null && snapshot.getId() <= lastSnapshot.getId()) {
            logger.warn("Trying to store outdated snapshot. Last snapshot id: {}, new snapshot id: {}",
                    last.getId(), snapshot.getId());
            return;
        }

        File tmpFile = new File(storagePath + File.separator + SNAPSHOT_FILE_PREFIX + snapshot.getId() + ".tmp");
        try (FileOutputStream fOut = new FileOutputStream(tmpFile);
             BufferedOutputStream output = new BufferedOutputStream(fOut)) {
            byte[] lenBytes = new byte[4];

            // msg len + msg
            int msgLen = snapshot.getSerializedSize();
            Bits.putInt(lenBytes, 0, msgLen);
            output.write(lenBytes);
            snapshot.writeTo(output);
            if (options.isSaveSnapshotSynchronously()) {
                fOut.getFD().sync();
            }
        }

        File destFile = new File(storagePath + File.separator + SNAPSHOT_FILE_PREFIX + snapshot.getId());
        atomicRenameFile(tmpFile.toPath(), destFile.toPath());

        lastSnapshot = snapshot;

        purgeSnapshots();
    }

    private void atomicRenameFile(Path tmpFilePath, Path destFilePath) throws IOException {
        // Move temp file to target path atomically.
        // The code comes from https://github.com/jenkinsci/jenkins/blob/master/core/src/main/java/hudson/util/AtomicFileWriter.java#L187
        try {
            Files.move(tmpFilePath, destFilePath, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            // If it falls here that can mean many things. Either that the atomic move is not supported,
            // or something wrong happened. Anyway, let's try to be over-diagnosing
            if (e instanceof AtomicMoveNotSupportedException) {
                logger.warn("Atomic move not supported. falling back to non-atomic move, error: {}.", e.getMessage());
            } else {
                logger.warn("Unable to move atomically, falling back to non-atomic move, error: {}.", e.getMessage());
            }

            if (Files.exists(destFilePath, LinkOption.NOFOLLOW_LINKS)) {
                logger.info("The target file {} was already existing.", destFilePath);
            }

            try {
                Files.move(tmpFilePath, destFilePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e1) {
                e1.addSuppressed(e);
                logger.warn("Unable to move {} to {}. Attempting to delete {} and abandoning.", tmpFilePath, destFilePath, tmpFilePath);
                try {
                    Files.deleteIfExists(tmpFilePath);
                } catch (IOException e2) {
                    e2.addSuppressed(e1);
                    logger.warn("Unable to delete {}!", tmpFilePath);
                    throw e2;
                }

                throw e1;
            }
        }
    }

    private void loadLastSnapshot() throws IOException {
        if (storagePath == null) {
            return;
        }

        List<Path> availableSnapshots = listAllAvailableSnapshotPath(true);
        if (!availableSnapshots.isEmpty()) {
            Path lastSnapshotPath = availableSnapshots.get(0);
            if (lastSnapshotPath != null) {
                lastSnapshot = loadSnapshotOnPath(lastSnapshotPath);

                logger.info("Found last snapshot on path {} with id {}", lastSnapshotPath, lastSnapshot.getId());
            }
        }
    }

    public List<Path> listAllAvailableSnapshotPath(boolean sort) throws IOException {
        Stream<Path> paths = Files.list(storagePath)
                .filter(p -> p.getFileName().toString().startsWith(SNAPSHOT_FILE_PREFIX));
        if (sort) {
            return paths
                    .collect(Collectors.toMap((Path p) -> {
                                try {
                                    String id = p.getFileName().toString().substring(SNAPSHOT_FILE_PREFIX.length());
                                    return Long.valueOf(id);
                                } catch (NumberFormatException ex) {
                                    return 0L;
                                }
                            }
                            ,
                            v -> v
                    ))
                    .entrySet()
                    .stream()
                    .sorted((o1, o2) -> {
                        if (o1.getKey() < o2.getKey()) {
                            return 1;
                        } else if (o1.getKey().equals(o2.getKey())) {
                            return 0;
                        } else {
                            return -1;
                        }
                    })
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        } else {
            return paths.collect(Collectors.toList());
        }
    }

    /**
     * Purge exceed snapshots. Can only be called with the protect of purgingOldSnapshots or in Constructor.
     *
     * @param availableSnapshots current snapshot paths
     */
    private void purgeSnapshots(List<Path> availableSnapshots) {
        if (availableSnapshots.size() > options.getMaxSnapshotsToKeep()) {
            List<Path> pathsToPurge = availableSnapshots.subList(options.getMaxSnapshotsToKeep(), availableSnapshots.size());
            for (Path path : pathsToPurge) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    logger.warn("Delete old snapshot file: {} failed", path.toString());
                }
            }
        }
    }

    private void purgeSnapshots() {
        if (purgingOldSnapshots.compareAndSet(false, true)) {
            try {
                purgeSnapshots(listAllAvailableSnapshotPath(true));
            } catch (IOException ex) {
                logger.error("Purge expired snapshots failed", ex);
            } finally {
                purgingOldSnapshots.set(false);
            }
        }
    }

    public Snapshot loadSnapshotOnPath(Path filePath) throws IOException {
        File file = filePath.toFile();

        assert file.exists() : filePath;

        byte[] lenBytes = new byte[4];
        try (FileInputStream fin = new FileInputStream(file);
             BufferedInputStream input = new BufferedInputStream(fin)) {
            readBytes(lenBytes, input);
            int msgLen = Bits.getInt(lenBytes, 0);
            byte[] msgBytes = new byte[msgLen];
            readBytes(msgBytes, input);

            return Snapshot.parseFrom(msgBytes);
        }
    }

    private void readBytes(final byte[] bs, final InputStream input) throws IOException {
        int read;
        if ((read = input.read(bs)) != bs.length) {
            throw new IOException("Read error, expects " + bs.length + " bytes, but read " + read);
        }
    }

    public Snapshot getLastSnapshot() {
        return lastSnapshot;
    }

}
