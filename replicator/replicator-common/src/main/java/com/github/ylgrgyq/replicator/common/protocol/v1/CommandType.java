package com.github.ylgrgyq.replicator.common.protocol.v1;

public enum CommandType {
    ONE_WAY((byte) 0),
    REQUEST((byte) 1),
    RESPONSE((byte) 2);

    private byte code;

    CommandType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
