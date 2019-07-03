package com.github.ylgrgyq.replicator.server;


import com.github.ylgrgyq.replicator.proto.Snapshot;

public interface SnapshotGenerator {
    Snapshot generateSnapshot();
}
