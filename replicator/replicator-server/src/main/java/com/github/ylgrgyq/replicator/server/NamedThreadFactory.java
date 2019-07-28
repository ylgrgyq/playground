package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class NamedThreadFactory implements ThreadFactory {
    private final AtomicLong threadIndex = new AtomicLong(0);
    private final String threadNamePrefix;

    public NamedThreadFactory(final String threadNamePrefix) {
        if (threadNamePrefix.endsWith("-")){
            this.threadNamePrefix = threadNamePrefix;
        } else {
            this.threadNamePrefix  = threadNamePrefix + "-";
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, threadNamePrefix + this.threadIndex.incrementAndGet());

    }
}

