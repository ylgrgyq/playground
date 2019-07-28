package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.server.storage.StorageHandle;
import org.rocksdb.ColumnFamilyHandle;

public class RocksDbStorageHandle implements StorageHandle {
    private ColumnFamilyHandle handle;
    private String topic;

    public RocksDbStorageHandle(String topic, ColumnFamilyHandle handle) {
        this.topic = topic;
        this.handle = handle;
    }

    public ColumnFamilyHandle getColumnFamilyHandle() {
        return handle;
    }

    public String getTopic() {
        return topic;
    }
}
