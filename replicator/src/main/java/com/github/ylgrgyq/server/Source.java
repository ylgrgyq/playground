package com.github.ylgrgyq.server;

import com.github.ylgrgyq.server.storage.MemoryStorage;
import com.github.ylgrgyq.server.storage.Storage;

import java.util.List;

public class Source {
    private String topic;
    private Storage storage;
    private long pendingSize;
    private long nextIndex;
    private SnapshotGenerator snapshotGenerator;
    private Snapshot lastSnapshot;
    private long maxPendingLogSize;

    public Source(String topic, SourceOptions options) {
        this.topic = topic;
        this.snapshotGenerator = options.getSnapshotGenerator();
        this.storage = new MemoryStorage();
        this.maxPendingLogSize = options.getMaxPendingLogSize();
    }

    public void init() {
        storage.init();
    }

    public void append(byte[] data) {
        Index index = new Index(topic, nextIndex);
        nextIndex = nextIndex + 1;
        storage.append(data);
        pendingSize = pendingSize + data.length;

        if (pendingSize >= maxPendingLogSize) {
            lastSnapshot = snapshotGenerator.generateSnapshot();
            storage.trimToIndex(lastSnapshot.getSnapshotIndex());
        }
    }

    public List<byte[]> getEntries(Index fromIndex, int limit) {
        if (fromIndex.getIndex() < lastSnapshot.getSnapshotIndex().getIndex()) {
            throw new RuntimeException("fetch last snapshot");
        }

        return storage.getEntries(fromIndex, limit);
    }

    public Snapshot getSnapshot() {
        if (lastSnapshot != null) {
            return lastSnapshot;
        }

        return null;
    }
}
