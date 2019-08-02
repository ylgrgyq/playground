package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.common.RemotingCommand;

import java.util.concurrent.CompletableFuture;

public interface ReplicatorClient {
    CompletableFuture<Void> start();
    CompletableFuture<Void> shutdown();
    void onChannelActive(NettyReplicateChannel channel);
    void onRetryFetchLogs();
    void onReceiveRemotingMsg(RemotingCommand cmd);


}
