package com.github.ylgrgyq.replicator.client;

public class ReplicatorException extends RuntimeException {

    private ReplicatorError error;

    public ReplicatorException(ReplicatorError error) {
        this.error = error;
    }

    public ReplicatorException(ReplicatorError error, Throwable cause) {
        super(cause);
        this.error = error;
    }

    public ReplicatorError getError() {
        return error;
    }

    @Override
    public String toString() {
        return String.format("Replicator client error: %s",  error);
    }
}
