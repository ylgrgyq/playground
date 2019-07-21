package com.github.ylgrgyq.replicator.client;

import java.util.List;

public interface StateMachine {
    void apply(List<byte[]> logs);

    void snapshot(byte[] snapshot);

    void reset();
}
