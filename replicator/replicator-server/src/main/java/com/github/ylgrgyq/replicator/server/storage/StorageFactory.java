package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.server.storage.rocksdb.RocksDbStorage;

public class StorageFactory {
    public static Storage<? extends StorageHandle> createStorage(StorageOptions options) throws InterruptedException{
        return new RocksDbStorage(options);
    }
}
