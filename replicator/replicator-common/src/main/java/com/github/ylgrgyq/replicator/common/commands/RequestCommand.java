package com.github.ylgrgyq.replicator.common.commands;

public abstract class RequestCommand extends RemotingCommand {

    protected RequestCommand(byte version) {
        super(CommandType.REQUEST, version);
    }

    protected RequestCommand(CommandType commandType, byte msgVersion) {
        super(commandType, msgVersion);
    }
}
