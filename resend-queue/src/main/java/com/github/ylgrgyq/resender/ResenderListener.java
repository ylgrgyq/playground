package com.github.ylgrgyq.resender;

public interface ResenderListener<E> {
    void onInvalidPayload(PayloadCarrier<E> failedPayload);
    void onPayloadSendSuccess(PayloadCarrier<E> failedPayload);
    void onPayloadSendFailed(PayloadCarrier<E> failedPayload);
    void onNotificationFailed(Throwable throwable);
}
