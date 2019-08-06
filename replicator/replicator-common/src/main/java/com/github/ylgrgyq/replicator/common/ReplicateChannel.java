package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;

public interface ReplicateChannel {
    void writeError(ReplicatorError error);
    void writeRemoting(RemotingCommand cmd);
    void close();
}
