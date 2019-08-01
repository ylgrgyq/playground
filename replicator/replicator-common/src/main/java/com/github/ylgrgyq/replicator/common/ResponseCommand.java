package com.github.ylgrgyq.replicator.common;

public class ResponseCommand extends RemotingCommand{
    private Object responseObject;

    public ResponseCommand() {
        super(CommandType.RESPONSE);
    }

    public <T> T getResponseObject() {
        return getBody();
    }

    public void setResponseObject(Object responseObject) {
        setBody(responseObject);
    }

    @Override
    void serialize() {

    }

    @Override
    void deserialize() {

    }
}
