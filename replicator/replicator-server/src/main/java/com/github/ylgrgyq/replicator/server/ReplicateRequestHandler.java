package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.protocol.v1.FetchLogsRequestCommand;
import com.github.ylgrgyq.replicator.server.sequence.SequenceImpl;

public interface ReplicateRequestHandler {
    void onStart(ReplicatorRemotingContext ctx, SequenceImpl seq);
    void handleFetchLogs(ReplicatorRemotingContext ctx, FetchLogsRequestCommand fetchlogs);
    void handleFetchSnapshot(ReplicatorRemotingContext ctx);
    void onFinish();
}
