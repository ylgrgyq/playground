package com.github.ylgrgyq.resender;

public final class PayloadWithId {
    private final long id;
    private final byte[] payload;

    public PayloadWithId(long id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload;
    }
}
