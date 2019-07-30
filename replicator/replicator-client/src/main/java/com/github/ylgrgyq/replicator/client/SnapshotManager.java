package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.client.connection.websocket.ReplicatorClientHandler;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.stream.Collectors;

public class SnapshotManager {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private static final String SNAPSHOT_FILE_PREFIX = "replicator_snapshot_";
    private String path;
    private ReplicatorClientOptions options;
    private Snapshot lastSnapshot;

    public SnapshotManager(ReplicatorClientOptions options) throws IOException {
        this.options = options;
        this.path = options.getSnapshotStoragePath();

        if (path != null) {
            final File dir = new File(this.path);
            if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Invalid snapshot storage path, it's a regular file: " + this.path);
            }

            FileUtils.forceMkdir(new File(this.path));
        } else {
            logger.warn("No snapshot storage path provided, snapshot will not be saved persistently");
        }
    }

    public void storeSnapshot(Snapshot snapshot) throws IOException {
        if (path == null) {
            return;
        }

        File tmpFile = new File(path + File.separator + SNAPSHOT_FILE_PREFIX + snapshot.getId() + ".tmp");
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

        File destFile = new File(path + File.separator + SNAPSHOT_FILE_PREFIX + snapshot.getId());
        atomicRenameFile(tmpFile.toPath(), destFile.toPath());

        lastSnapshot = snapshot;
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

    public void loadLastSnapshot() throws IOException {
        if (path == null) {
            return;
        }

        Path lastSnapshotPath = searchLastSnapshotPath();
        if (lastSnapshotPath != null) {
            lastSnapshot = readSnapshot(lastSnapshotPath);

            logger.info("Found last snapshot on path {} with id {}", lastSnapshotPath, lastSnapshot.getId());
        }
    }

    private Path searchLastSnapshotPath() throws IOException{
        Path storageDirPath = Paths.get(path);
        return Files.list(storageDirPath)
                .filter(p -> p.getFileName().startsWith(SNAPSHOT_FILE_PREFIX))
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
                        return -1;
                    } else if (o1.getKey().equals(o2.getKey())) {
                        return 0;
                    } else {
                        return 1;
                    }
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Snapshot readSnapshot(Path filePath) throws IOException {
        File file = new File(this.path);

        assert file.exists() : filePath;

        byte[] lenBytes = new byte[4];
        try (FileInputStream fin = new FileInputStream(file);
             BufferedInputStream input = new BufferedInputStream(fin)) {
            readBytes(lenBytes, input);
            int len = Bits.getInt(lenBytes, 0);
            if (len <= 0) {
                throw new IOException("Invalid topic.");
            }

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
