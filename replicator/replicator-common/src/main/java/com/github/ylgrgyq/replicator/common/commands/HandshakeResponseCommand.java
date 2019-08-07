package com.github.ylgrgyq.replicator.common.commands;

@CommandFactoryManager.AutoLoad
public final class HandshakeResponseCommand extends ResponseCommand {
    private static final byte VERSION = 1;

    public HandshakeResponseCommand() {
        super(VERSION);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.HANDSHAKE;
    }

    @Override
    public void serialize() {
    }

    @Override
    public void deserialize(byte[] content) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "HandshakeResponse{" +
                super.toString() +
                '}';
    }
}