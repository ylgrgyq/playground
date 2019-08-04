package com.github.ylgrgyq.replicator.common.protocol.v1;

import org.junit.Test;

import static org.junit.Assert.*;

public class HandshakeRequestCommandTest {

    @Test
    public void serializeNormal() throws Exception {
        HandshakeRequestCommand expect = new HandshakeRequestCommand();
        expect.setTopic("hello");

        expect.serialize();

        HandshakeRequestCommand actual = new HandshakeRequestCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }

    @Test
    public void serializeEmptyTopicCommand() throws Exception {
        HandshakeRequestCommand expect = new HandshakeRequestCommand();

        expect.serialize();

        HandshakeRequestCommand actual = new HandshakeRequestCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}