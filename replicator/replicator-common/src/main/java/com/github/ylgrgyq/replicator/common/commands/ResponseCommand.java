package com.github.ylgrgyq.replicator.common.commands;

public abstract class ResponseCommand extends RemotingCommand {
    protected ResponseCommand(byte version) {
        super(CommandType.RESPONSE, version);
    }
}
