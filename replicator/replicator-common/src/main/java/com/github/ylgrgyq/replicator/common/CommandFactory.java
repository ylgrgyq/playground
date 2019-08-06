package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;

public interface CommandFactory <T extends RemotingCommand> {
    T createCommand();
}
