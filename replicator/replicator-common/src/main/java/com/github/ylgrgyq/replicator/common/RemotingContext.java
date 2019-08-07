package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;
import com.github.ylgrgyq.replicator.common.commands.ResponseCommand;
import com.github.ylgrgyq.replicator.common.commands.CommandFactoryManager;
import com.github.ylgrgyq.replicator.common.commands.MessageType;

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
    public void sendResponse(ResponseCommand responseObject) {
        channel.writeRemoting(responseObject);
    }

    protected ReplicateChannel getChannel() {
        return channel;
    }
}
