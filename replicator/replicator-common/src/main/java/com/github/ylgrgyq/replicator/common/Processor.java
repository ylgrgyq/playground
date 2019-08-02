package com.github.ylgrgyq.replicator.common;

public interface Processor<C, T> {
    void process(C ctx, T cmd);
}
