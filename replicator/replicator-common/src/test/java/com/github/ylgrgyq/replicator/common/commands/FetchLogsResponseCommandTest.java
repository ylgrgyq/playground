package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FetchLogsResponseCommandTest {

    @Test
    public void serialize() throws Exception {
        ArrayList<LogEntry> logs = new ArrayList<>();
        LogEntry e = new LogEntry();
        e.setId(10);
        e.setData("Hello".getBytes());
        logs.add(e);

        e = new LogEntry();
        e.setId(11);
        e.setData("world!".getBytes());
        logs.add(e);

        e = new LogEntry();
        e.setId(15);
        e.setData("He".getBytes());
        logs.add(e);

        e = new LogEntry();
        e.setId(101);
        e.setData("'s a good man.".getBytes());
        logs.add(e);

        e = new LogEntry();
        e.setId(1);
        e.setData("Yes".getBytes());
        logs.add(e);

        FetchLogsResponseCommand expect = new FetchLogsResponseCommand();
        expect.setLogs(logs);

        expect.serialize();

        FetchLogsResponseCommand actual = new FetchLogsResponseCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertEquals(logs, actual.getLogs());
    }

    @Test
    public void serializeEmptyResponse() throws Exception {
        FetchLogsResponseCommand expect = new FetchLogsResponseCommand();

        expect.serialize();

        FetchLogsResponseCommand actual = new FetchLogsResponseCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertTrue(actual.getLogs().isEmpty());
    }
}