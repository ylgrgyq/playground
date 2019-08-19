package com.github.ylgrgyq.resender;

public interface ConsumeObjectHandler<E extends Payload> {
    void handleBackupPayload(E payload) throws Exception;

    boolean handleFailedPayload(E failedPayload, Throwable throwable);
}
