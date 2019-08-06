package com.github.ylgrgyq.replicator.common.commands;

public abstract class RequestCommand extends RemotingCommand {

    protected RequestCommand(MessageType type, byte version) {
        super(CommandType.REQUEST, type, version);
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
