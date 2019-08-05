package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class FetchSnapshotResponseCommandTest {

    @Test
    public void serializeNormal() throws Exception{
        Snapshot expectSnapshot = Snapshot.newBuilder()
                .setId(1010101)
                .setData(ByteString.copyFrom("Hello world".getBytes(StandardCharsets.UTF_8)))
                .build();

        FetchSnapshotResponseCommand expect = new FetchSnapshotResponseCommand();
        expect.setSnapshot(expectSnapshot);
        expect.serialize();

        FetchSnapshotResponseCommand actual = new FetchSnapshotResponseCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
        assertEquals(expectSnapshot, actual.getSnapshot());
    }

    @Test
    public void serializeEmptySnapshot() throws Exception{
        FetchSnapshotResponseCommand expect = new FetchSnapshotResponseCommand();
        expect.serialize();

        FetchSnapshotResponseCommand actual = new FetchSnapshotResponseCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}