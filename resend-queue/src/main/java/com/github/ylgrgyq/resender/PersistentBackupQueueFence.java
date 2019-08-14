package com.github.ylgrgyq.resender;

public interface PersistentBackupQueueFence {
    boolean allowPersistent(BackupQueue<?> queue);

    void updateFence(BackupQueue<?> queue);
}
