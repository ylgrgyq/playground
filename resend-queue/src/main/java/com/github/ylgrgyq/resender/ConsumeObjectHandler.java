package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;

public interface ConsumeObjectHandler<E extends Verifiable> {
    void onHandleObject(@Nonnull E obj) throws Exception;

    @Nonnull
    HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable);
}
