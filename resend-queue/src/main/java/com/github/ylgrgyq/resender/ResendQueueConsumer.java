package com.github.ylgrgyq.resender;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class ResendQueueConsumer<E> {
    private BackupStorage<PayloadCarrier<E>> storage;
    public ResendQueueConsumer(BackupStorage<PayloadCarrier<E>> storage) {
        this.storage = storage;
    }

    E fetch() {
        return null;
    }

    E fetch(long timeout, TimeUnit unit) {
        return null;
    }

    E commit() {
        return null;
    }


}
