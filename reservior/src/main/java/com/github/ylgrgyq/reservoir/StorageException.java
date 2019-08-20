package com.github.ylgrgyq.reservoir;

public class StorageException extends Exception {
    public StorageException() {
        super();
    }

    public StorageException(String message) {
        super(message);
    }

    public StorageException(Throwable throwable) {
        super(throwable);
    }

    public StorageException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
