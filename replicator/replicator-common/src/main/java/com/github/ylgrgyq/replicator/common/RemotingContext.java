package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.protocol.v1.CommandFactoryManager;
import com.github.ylgrgyq.replicator.common.protocol.v1.MessageType;

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
        ResponseCommand resp = CommandFactoryManager.createResponse(command);
        resp.setResponseObject(responseObject);
        channel.writeRemoting((RemotingCommand)responseObject);
    }

    protected ReplicateChannel getChannel() {
        return channel;
    }
}
