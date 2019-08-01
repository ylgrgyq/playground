package com.github.ylgrgyq.replicator.common;

public class Protocol {
    /**
     * MAGIC for replicator protocol
     */
    public static final byte PROTOCOL_MAGIC = (byte) 0xBE;
    /**
     *  Current protocol version
     */
    public static final byte PROTOCOL_VERSION = (byte) 1;

    private static final int REQUEST_HEADER_LEN  = 7 + 2;
    private static final int RESPONSE_HEADER_LEN = 7 + 2;

    public static int getRequestHeaderLength() {
        return Protocol.REQUEST_HEADER_LEN;
    }

    public static int getResponseHeaderLength() {
        return Protocol.RESPONSE_HEADER_LEN;
    }
}
