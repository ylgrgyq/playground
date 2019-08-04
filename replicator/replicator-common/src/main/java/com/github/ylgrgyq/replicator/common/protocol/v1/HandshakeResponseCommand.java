package com.github.ylgrgyq.replicator.common.protocol.v1;

@CommandFactoryManager.AutoLoad
public final class HandshakeResponseCommand extends ResponseCommandV1 {
    public HandshakeResponseCommand() {
        super(MessageType.HANDSHAKE);
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