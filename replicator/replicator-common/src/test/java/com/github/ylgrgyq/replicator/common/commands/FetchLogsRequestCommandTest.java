package com.github.ylgrgyq.replicator.common.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FetchLogsRequestCommandTest {

    @Test
    public void serialize() throws Exception{
        long fromId = 1010101;
        int limit = 10101;

        FetchLogsRequestCommand expect = new FetchLogsRequestCommand();
        expect.setFromId(fromId);
        expect.setLimit(limit);

        expect.serialize();

        FetchLogsRequestCommand actual = new FetchLogsRequestCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertEquals(fromId, actual.getFromId());
        assertEquals(limit, actual.getLimit());
    }
}