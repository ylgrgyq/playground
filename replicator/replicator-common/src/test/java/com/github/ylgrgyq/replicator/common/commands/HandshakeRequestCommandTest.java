package com.github.ylgrgyq.replicator.common.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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