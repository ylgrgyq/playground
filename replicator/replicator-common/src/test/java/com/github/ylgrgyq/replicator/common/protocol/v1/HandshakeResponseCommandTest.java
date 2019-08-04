package com.github.ylgrgyq.replicator.common.protocol.v1;

import org.junit.Test;

import static org.junit.Assert.*;

public class HandshakeResponseCommandTest {

    @Test
    public void serialize() {
        HandshakeResponseCommand expect = new HandshakeResponseCommand();

        expect.serialize();

        HandshakeResponseCommand actual = new HandshakeResponseCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}