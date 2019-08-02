package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.common.ReplicateChannel;

public interface ReplicatorClient {
    void onStart(ReplicateChannel channel);

    void onReceiveRemotingMsgTimeout();

    void onReceiveRemotingMsg(RemotingCommand cmd);

    void shutdown() throws Exception;
}
