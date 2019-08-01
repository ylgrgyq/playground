package com.github.ylgrgyq.replicator.common;

public enum MessageType {
    UNKNOWN((byte) 0),
    HANDSHAKE((byte) 1),
    FETCH_LOGS((byte) 2),
    FETCH_SNAPSHOT((byte) 3),
    ERROR((byte) 4);

    public static final byte VERSION = 1;

    private byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType findMessageTypeByCode(byte code) {
        for (MessageType t : MessageType.values()){
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
