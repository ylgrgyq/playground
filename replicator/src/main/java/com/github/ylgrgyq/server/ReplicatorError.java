package com.github.ylgrgyq.server;

public enum ReplicatorError {
    UNKNOWN(-1, "Unknown error"),

    ENEEDCATCHUP(10001, "Please fetch recent snapshot to catch up"),

    EUNKNOWNPROTOCOL(10002, "Unknown protocol"),
    ;


    private int errorCode;
    private String msg;

    ReplicatorError(int errorCode, String msg) {
        this.errorCode = errorCode;
        this.msg = msg;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMsg() {
        return msg;
    }
}
