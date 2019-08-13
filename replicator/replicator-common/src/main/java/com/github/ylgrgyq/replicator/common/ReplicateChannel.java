package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;

import java.util.concurrent.CompletableFuture;

public interface ReplicateChannel {
    void writeError(ReplicatorError error);
    void writeRemoting(RemotingCommand cmd);
    CompletableFuture<Void> close();
}
