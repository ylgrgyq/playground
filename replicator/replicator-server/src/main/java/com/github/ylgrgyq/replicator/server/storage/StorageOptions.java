package com.github.ylgrgyq.replicator.server.storage;

import static com.github.ylgrgyq.replicator.server.Preconditions.checkArgument;

public final class StorageOptions {
    private final String storagePath;
    private final boolean destroyPreviousDbFiles;

    private StorageOptions(StorageOptionsBuilder builder) {
        this.storagePath = builder.storagePath;
        this.destroyPreviousDbFiles = builder.destroyPreviousDbFiles;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public boolean isDestroyPreviousDbFiles() {
        return destroyPreviousDbFiles;
    }

    public static StorageOptionsBuilder builder() {
        return new StorageOptionsBuilder();
    }

    public static class StorageOptionsBuilder {
        private String storagePath;
        private boolean destroyPreviousDbFiles = false;

        public StorageOptionsBuilder setStoragePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public StorageOptionsBuilder setDestroyPreviousDbFiles(boolean destroyPreviousDbFiles) {
            this.destroyPreviousDbFiles = destroyPreviousDbFiles;

            return this;
        }

        public StorageOptions build(){
            checkArgument(storagePath != null, "Please provide storage path");

            return new StorageOptions(this);
        }
    }
}
