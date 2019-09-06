package com.github.ylgrgyq.reservoir;

public interface ConsumeObjectHandler<E extends Verifiable> {
    void onHandleObject(E obj) throws Exception;

    HandleFailedStrategy onHandleObjectFailed(E obj, Throwable throwable);
}
