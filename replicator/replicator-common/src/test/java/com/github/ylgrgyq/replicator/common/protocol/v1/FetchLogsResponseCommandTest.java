package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class FetchLogsResponseCommandTest {

    @Test
    public void serialize() throws Exception {
        ArrayList<LogEntry> logs = new ArrayList<>();
        logs.add(LogEntry.newBuilder()
                .setId(10)
                .setData(ByteString.copyFrom("Hello".getBytes()))
                .build());
        logs.add(LogEntry.newBuilder()
                .setId(11)
                .setData(ByteString.copyFrom("world!".getBytes()))
                .build());
        logs.add(LogEntry.newBuilder()
                .setId(15)
                .setData(ByteString.copyFrom("He".getBytes()))
                .build());
        logs.add(LogEntry.newBuilder()
                .setId(101)
                .setData(ByteString.copyFrom("'s a good man.".getBytes()))
                .build());
        logs.add(LogEntry.newBuilder()
                .setId(1)
                .setData(ByteString.copyFrom("Yes".getBytes()))
                .build());

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