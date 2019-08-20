package com.github.ylgrgyq.reservoir;

public interface ConsumeObjectListener<E extends Verifiable> {
    void onInvalidObject( E obj);

    void onHandleSuccess( E obj);

    void onHandleFailed( E obj,  Throwable throwable);

    void onListenerNotificationFailed( Throwable throwable);
}
