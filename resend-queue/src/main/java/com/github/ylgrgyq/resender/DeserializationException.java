package com.github.ylgrgyq.resender;

public final class DeserializationException extends Exception {
    public DeserializationException() {
        super();
    }

    public DeserializationException(String message) {
        super(message);
    }

    public DeserializationException(Throwable throwable) {
        super(throwable);
    }

    public DeserializationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
