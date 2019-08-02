package com.github.ylgrgyq.replicator.common;

public interface ReplicateChannel {
    void writeError(ReplicatorError error);
    void writeRemoting(RemotingCommand cmd);
    void close();
}
