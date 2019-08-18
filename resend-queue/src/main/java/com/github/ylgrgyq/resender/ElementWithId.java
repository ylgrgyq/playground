package com.github.ylgrgyq.resender;

public final class ElementWithId {
    private final long id;
    private final byte[] element;

    public ElementWithId(long id, byte[] element) {
        this.id = id;
        this.element = element;
    }

    public long getId() {
        return id;
    }

    public byte[] getElement() {
        return element;
    }
}
