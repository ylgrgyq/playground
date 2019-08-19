package com.github.ylgrgyq.resender;

public interface ConsumeObjectListener<E extends Payload> {
    void onInvalidPayload(E failedPayload);
    void onPayloadSendSuccess(E failedPayload);
    void onPayloadSendFailed(E failedPayload);
    void onNotificationFailed(Throwable throwable);
}
