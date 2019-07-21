package com.github.ylgrgyq.replicator.client;

public enum ReplicatorError {
    UNKNOWN(-1, "Unknown error"),

    ENEED_CATCHUP(10001, "Please fetch recent snapshot to catch up"),

    EUNKNOWN_PROTOCOL(10002, "Unknown protocol"),

    ENEED_HANDSHAKE(10003, "Need handshake first, then send other requests"),

    ESTATEMACHINE_QUEUE_FULL(10004, "State machine queue full"),

    ECLIENT_ALREADY_SHUTDOWN(10005, "Client already shutdown"),

    ESTATEMACHINE_ALREADY_SHUTDOWN(10006, "State machine already shutdown"),
    ESTATEMACHINE_EXECUTION_ERROR(10007, "State machine execution error")
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
