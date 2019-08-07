package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.commands.FetchLogsRequestCommand;
import com.github.ylgrgyq.replicator.common.commands.FetchSnapshotRequestCommand;
import com.github.ylgrgyq.replicator.common.commands.HandshakeRequestCommand;
import com.github.ylgrgyq.replicator.server.sequence.SequenceImpl;

public interface ReplicateRequestHandler {
    void onStart(ReplicatorRemotingContext ctx, SequenceImpl seq, HandshakeRequestCommand req);
    void handleFetchLogs(ReplicatorRemotingContext ctx, FetchLogsRequestCommand fetchlogs);
    void handleFetchSnapshot(ReplicatorRemotingContext ctx, FetchSnapshotRequestCommand fetchSnapshot);
    void onFinish();
}
