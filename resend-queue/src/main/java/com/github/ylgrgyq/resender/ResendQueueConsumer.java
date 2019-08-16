package com.github.ylgrgyq.resender;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ResendQueueConsumer<E extends Payload> {
    private BackupStorage<PayloadWithId> storage;
    private BlockingQueue<E> queue;

    public ResendQueueConsumer(BackupStorage<PayloadWithId> storage) {
        this.storage = storage;
        this.queue = new ArrayBlockingQueue<>(1000);
    }

    public E fetch() throws InterruptedException {
        return queue.take();
    }

    public E fetch(long timeout, TimeUnit unit) throws InterruptedException {
        return null;
    }

    public E commit() {
        return null;
    }


}
