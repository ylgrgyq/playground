package com.github.ylgrgyq.replicator.common.commands;

@CommandFactoryManager.AutoLoad
public final class HandshakeResponseCommand extends ResponseCommand {
    private static final byte VERSION = 1;

    public HandshakeResponseCommand() {
        super(MessageType.HANDSHAKE, VERSION);
    }

    @Override
    public void serialize() {
    }

    @Override
    public void deserialize() {
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