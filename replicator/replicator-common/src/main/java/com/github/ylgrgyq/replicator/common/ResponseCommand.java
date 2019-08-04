package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.protocol.v1.CommandType;
import com.github.ylgrgyq.replicator.common.protocol.v1.MessageType;

public abstract class ResponseCommand extends RemotingCommand {
    protected ResponseCommand(MessageType type, byte defaultMsgVersion) {
        super(CommandType.RESPONSE, type, defaultMsgVersion);
    }

    public <T> T getResponseObject() {
        return getBody();
    }

    public void setResponseObject(Object responseObject) {
        setBody(responseObject);
    }
}
