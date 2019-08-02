package com.github.ylgrgyq.replicator.common;

public interface Context {
    void sendResponse(Object responseObject);

    MessageType getRemotingCommandMessageType();
}
