package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;

public interface ConsumeObjectListener<E extends Verifiable> {
    void onInvalidObject(@Nonnull E obj);

    void onHandleSuccess(@Nonnull E obj);

    void onHandleFailed(@Nonnull E obj);

    void onListenerNotificationFailed(@Nonnull Throwable throwable);
}
