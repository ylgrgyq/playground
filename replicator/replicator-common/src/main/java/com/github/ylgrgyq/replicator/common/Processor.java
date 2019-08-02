package com.github.ylgrgyq.replicator.common;

public interface Processor<T> {
    void process(Context ctx, T cmd);
}
