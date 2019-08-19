package com.github.ylgrgyq.resender;

public interface ConsumeObjectHandler<E extends Payload> {
    void onReceivedObject(E payload) throws Exception;

    HandleFailedObjectStrategy onReceivedObjectFailed(E failedPayload, Throwable throwable);
}
