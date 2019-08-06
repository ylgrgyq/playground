package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.MessageType;

public interface Context {
    void sendResponse(Object responseObject);

    MessageType getRemotingCommandMessageType();
}
