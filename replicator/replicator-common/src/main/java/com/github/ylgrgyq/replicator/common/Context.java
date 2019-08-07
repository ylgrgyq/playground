package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.MessageType;
import com.github.ylgrgyq.replicator.common.commands.ResponseCommand;

public interface Context {
    void sendResponse(ResponseCommand responseObject);

    MessageType getRemotingCommandMessageType();
}
