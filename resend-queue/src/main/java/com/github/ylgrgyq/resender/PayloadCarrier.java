package com.github.ylgrgyq.resender;

public final class PayloadCarrier<E> {
    private long id;
    private E payload;

    public PayloadCarrier(long id, E payload) {
        this.id = id;
        this.payload = payload;
    }

    public PayloadCarrier(byte[] bytes){

    }

    public long getId() {
        return id;
    }

    public boolean isValid() {
        return false;
    }

    public E getPayload() {
        return payload;
    }

}
