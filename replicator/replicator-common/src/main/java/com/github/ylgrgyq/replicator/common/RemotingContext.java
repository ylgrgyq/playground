package com.github.ylgrgyq.replicator.common;

public class RemotingContext implements Context{
    private ReplicateChannel channel;
    private RemotingCommand command;

    public RemotingContext(ReplicateChannel channel, RemotingCommand command) {
        this.channel = channel;
        this.command = command;
    }

    @Override
    public MessageType getRemotingCommandMessageType() {
        return command.getMessageType();
    }

    @Override
    public void sendResponse(Object responseObject) {
        ResponseCommand resp = CommandFactory.createResponse(command);
        resp.setResponseObject(responseObject);
        channel.writeRemoting(resp);
    }

    protected ReplicateChannel getChannel() {
        return channel;
    }
}
