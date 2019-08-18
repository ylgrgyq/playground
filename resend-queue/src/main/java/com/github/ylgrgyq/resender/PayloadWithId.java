package com.github.ylgrgyq.resender;

final class PayloadWithId {
    private final long id;
    private final byte[] payload;

    PayloadWithId(long id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    long getId() {
        return id;
    }

    byte[] getPayload() {
        return payload;
    }
}
