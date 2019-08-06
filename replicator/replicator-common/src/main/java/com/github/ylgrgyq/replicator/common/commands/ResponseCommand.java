package com.github.ylgrgyq.replicator.common.commands;

public abstract class ResponseCommand extends RemotingCommand {
    protected ResponseCommand(MessageType type, byte version) {
        super(CommandType.RESPONSE, type, version);
    }

    public <T> T getResponseObject() {
        return getBody();
    }

    public void setResponseObject(Object responseObject) {
        setBody(responseObject);
    }
}
