package com.github.ylgrgyq.resender;

public interface ConsumeObjectListener<E extends Verifiable> {
    void onInvalidObject(E obj);

    void onHandleSuccess(E obj);

    void onHandleFailed(E obj);

    void onListenerNotificationFailed(Throwable throwable);
}
