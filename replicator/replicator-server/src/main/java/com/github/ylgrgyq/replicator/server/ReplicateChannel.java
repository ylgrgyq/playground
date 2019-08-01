package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.RemotingCommand;

public interface ReplicateChannel {
    void writeError(ReplicatorError error);
    void writeRemoting(RemotingCommand cmd);
}
