package com.github.ylgrgyq.resender;

public final class PayloadCarrier<E> {
    private long id;
    private E payload;

    public PayloadCarrier(long id, E payload) {
        this.id = id;
        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public E getPayload() {
        return payload;
    }

}
