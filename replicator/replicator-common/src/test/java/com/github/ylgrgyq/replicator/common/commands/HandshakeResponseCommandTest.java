package com.github.ylgrgyq.replicator.common.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HandshakeResponseCommandTest {

    @Test
    public void serialize() {
        HandshakeResponseCommand expect = new HandshakeResponseCommand();

        expect.serialize();

        HandshakeResponseCommand actual = new HandshakeResponseCommand();
        actual.deserialize(expect.getContent());

        assertEquals(expect, actual);
    }
}