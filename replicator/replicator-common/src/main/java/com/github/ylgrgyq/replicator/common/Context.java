package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.protocol.v1.MessageType;

public interface Context {
    void sendResponse(Object responseObject);

    MessageType getRemotingCommandMessageType();
}
