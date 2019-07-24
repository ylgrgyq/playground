package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.server.ReplicatorServerOptions;

public class StorageFactory {
    public static Storage createStorage(ReplicatorServerOptions options) {
        return new RocksDbStorage(options.getStoragePath());
    }
}
