package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.protocol.v1.CommandType;
import com.github.ylgrgyq.replicator.common.protocol.v1.MessageType;

public abstract class RequestCommand extends RemotingCommand {

    protected RequestCommand(MessageType type, byte defaultMsgVersion) {
        super(CommandType.REQUEST, type, defaultMsgVersion);
    }

    protected RequestCommand(CommandType commandType, MessageType msgType, byte msgVersion) {
        super(commandType, msgType, msgVersion);
    }

    public <T> T getRequestObject() {
        return getBody();
    }

    public void setRequestObject(Object requestObject) {
        setBody(requestObject);
    }
}
