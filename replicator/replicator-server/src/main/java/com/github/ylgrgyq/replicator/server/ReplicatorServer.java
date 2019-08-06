package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;
import com.github.ylgrgyq.replicator.common.ReplicateChannel;
import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceReader;

public interface ReplicatorServer {
    void onReceiveRemotingCommand(ReplicateChannel channel, Replica replica, RemotingCommand cmd);
    SequenceAppender createSequence(String topic, SequenceOptions options);
    SequenceReader getSequenceReader(String topic);
    void dropSequence(String topic);
    void shutdown() throws InterruptedException;
}
