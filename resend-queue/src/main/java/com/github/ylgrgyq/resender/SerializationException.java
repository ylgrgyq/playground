package com.github.ylgrgyq.resender;

public final class SerializationException extends Exception {
    public SerializationException() {
        super();
    }

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(Throwable throwable) {
        super(throwable);
    }

    public SerializationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
