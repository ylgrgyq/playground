package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.Snapshot;

public interface SnapshotGenerator {
    Snapshot generateSnapshot();
}
