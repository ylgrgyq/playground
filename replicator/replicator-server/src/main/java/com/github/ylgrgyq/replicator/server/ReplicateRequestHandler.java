package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.FetchLogsRequest;
import com.github.ylgrgyq.replicator.server.sequence.Sequence;

public interface ReplicateRequestHandler {
    void onStart(ReplicatorRemotingContext ctx, String topic, Sequence seq);
    void handleFetchLogs(ReplicatorRemotingContext ctx, FetchLogsRequest fetchlogs);
    void handleFetchSnapshot(ReplicatorRemotingContext ctx);
    void onFinish();
}
