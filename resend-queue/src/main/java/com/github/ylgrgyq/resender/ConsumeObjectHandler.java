package com.github.ylgrgyq.resender;

public interface ConsumeObjectHandler<E extends Verifiable> {
    void onHandleObject(E obj) throws Exception;

    HandleFailedStrategy onHandleObjectFailed(E obj, Throwable throwable);
}
