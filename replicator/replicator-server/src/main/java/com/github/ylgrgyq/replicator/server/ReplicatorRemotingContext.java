package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.common.RemotingContext;
import com.github.ylgrgyq.replicator.common.ReplicateChannel;
import com.github.ylgrgyq.replicator.common.ReplicatorError;

public class ReplicatorRemotingContext extends RemotingContext {
    private Replica replica;
    public ReplicatorRemotingContext(ReplicateChannel channel, RemotingCommand command, Replica replica) {
        super(channel, command);
        this.replica = replica;
    }

    public Replica getReplica() {
        return replica;
    }

    public void sendError(ReplicatorError error) {
        getChannel().writeError(error);
    }
}
