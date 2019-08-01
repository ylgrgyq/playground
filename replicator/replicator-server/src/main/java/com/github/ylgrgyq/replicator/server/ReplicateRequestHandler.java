package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.server.sequence.Sequence;

public interface ReplicateRequestHandler {
    void onStart(String topic, Sequence seq, RemotingCommand cmd);
    void handleSyncLogs(RemotingCommand cmd);
    void handleFetchSnapshot(RemotingCommand cmd);
    void onFinish();
}
