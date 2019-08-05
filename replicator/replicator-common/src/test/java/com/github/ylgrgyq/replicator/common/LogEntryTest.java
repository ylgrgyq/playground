package com.github.ylgrgyq.replicator.common;

import org.junit.Test;

import static org.junit.Assert.*;

public class LogEntryTest {

    @Test
    public void testSerialize() throws Exception {
        long expectId = 1010101;
        byte[] expectData = "Hello".getBytes();
        LogEntry expectEntry = new LogEntry();
        expectEntry.setId(expectId);
        expectEntry.setData(expectData);

        byte[] bs = expectEntry.serialize();

        LogEntry actualEntry = new LogEntry();
        actualEntry.deserialize(bs);

        assertEquals(expectEntry, actualEntry);
        assertEquals(1010101, actualEntry.getId());
        assertArrayEquals(expectData, actualEntry.getData());
    }
}