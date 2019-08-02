package com.github.ylgrgyq.replicator.common;

public interface Processor<T extends RemotingCommand> {
    void process(T cmd);
}
