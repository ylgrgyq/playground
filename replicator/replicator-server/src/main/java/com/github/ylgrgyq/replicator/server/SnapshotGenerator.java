package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.entity.Snapshot;

public interface SnapshotGenerator {
    Snapshot generateSnapshot();
}
