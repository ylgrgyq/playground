package com.github.ylgrgyq.replicator.common.exception;

import com.github.ylgrgyq.replicator.common.ReplicatorError;

public class ReplicatorException extends RuntimeException {

    private ReplicatorError error;

    public ReplicatorException(ReplicatorError error) {
        this.error = error;
    }

    public ReplicatorException(ReplicatorError error, Throwable cause) {
        super(cause);
        this.error = error;
    }

    public ReplicatorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReplicatorException(Throwable cause) {
        super(cause);
    }

    public ReplicatorError getError() {
        return error;
    }
}
