package com.github.ylgrgyq.replicator.common.protocol.v1;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FetchLogsRequestCommandTest {

    @Test
    public void serialize() throws Exception{
        FetchLogsRequestCommand expect = new FetchLogsRequestCommand();
        expect.setFromId(1010101);
        expect.setLimit(10101);

        expect.serialize();

        FetchLogsRequestCommand actual = new FetchLogsRequestCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}