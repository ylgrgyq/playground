package com.github.ylgrgyq.replicator.server.storage;

import static com.github.ylgrgyq.replicator.server.Preconditions.checkArgument;

public final class StorageOptions {
    private final String storagePath;

    private StorageOptions(StorageOptionsBuilder builder) {
        this.storagePath = builder.storagePath;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public static StorageOptionsBuilder builder() {
        return new StorageOptionsBuilder();
    }

    public static class StorageOptionsBuilder {
        private String storagePath;

        public StorageOptionsBuilder setStoragePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public StorageOptions build(){
            checkArgument(storagePath != null, "Please provide storage path");

            return new StorageOptions(this);
        }
    }
}
