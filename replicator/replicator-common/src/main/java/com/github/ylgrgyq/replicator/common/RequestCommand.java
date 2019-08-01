package com.github.ylgrgyq.replicator.common;

public class RequestCommand extends RemotingCommand{

    public RequestCommand() {
        super(CommandType.REQUEST);
    }

    protected RequestCommand(CommandType type) {
        super(type);
    }

    public <T> T getRequestObject() {
        return getBody();
    }

    public void setRequestObject(Object requestObject) {
        setBody(requestObject);
    }

    @Override
    void serialize() {

    }

    @Override
    void deserialize() {

    }
}
