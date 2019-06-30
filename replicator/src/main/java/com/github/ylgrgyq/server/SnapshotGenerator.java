package com.github.ylgrgyq.server;

import com.github.ylgrgyq.proto.Snapshot;

public interface SnapshotGenerator {
    Snapshot generateSnapshot();
}
