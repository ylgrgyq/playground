package com.github.ylgrgyq.replicator.common.exception;

public class SerializationException extends CodecException {
    /**
     * Constructor.
     */
    public SerializationException() {

    }

    /**
     * Constructor.
     */
    public SerializationException(String message) {
        super(message);
    }

    /**
     * Constructor.
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
