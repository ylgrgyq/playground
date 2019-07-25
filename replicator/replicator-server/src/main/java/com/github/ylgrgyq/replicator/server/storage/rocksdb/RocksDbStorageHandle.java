package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.server.storage.StorageHandle;
import org.rocksdb.ColumnFamilyHandle;

public class RocksDbStorageHandle implements StorageHandle {
    private ColumnFamilyHandle handle;

    public RocksDbStorageHandle(ColumnFamilyHandle handle) {
        this.handle = handle;
    }

    public ColumnFamilyHandle getColumnFailyHandle() {
        return handle;
    }
}
