package com.github.ylgrgyq.resender;

public interface BackupQueueHandler<E> {
    void handleBackupPayload(E payload) throws Exception;

    boolean handleFailedPayload(E failedPayload, Throwable throwable);
}
