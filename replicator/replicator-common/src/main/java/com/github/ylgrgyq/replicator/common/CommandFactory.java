package com.github.ylgrgyq.replicator.common;

public interface CommandFactory <T extends RemotingCommand> {
    T createCommand();
}
