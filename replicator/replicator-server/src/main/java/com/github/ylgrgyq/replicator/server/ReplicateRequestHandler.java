package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.FetchLogsRequest;
import com.github.ylgrgyq.replicator.server.sequence.SequenceImpl;

public interface ReplicateRequestHandler {
    void onStart(ReplicatorRemotingContext ctx, SequenceImpl seq);
    void handleFetchLogs(ReplicatorRemotingContext ctx, FetchLogsRequest fetchlogs);
    void handleFetchSnapshot(ReplicatorRemotingContext ctx);
    void onFinish();
}
