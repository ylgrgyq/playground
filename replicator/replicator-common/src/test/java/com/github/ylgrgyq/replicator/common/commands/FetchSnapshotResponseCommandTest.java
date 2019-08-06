package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class FetchSnapshotResponseCommandTest {

    @Test
    public void serializeNormal() throws Exception{
        Snapshot expectSnapshot = new Snapshot();
        expectSnapshot.setId(1010101);
        expectSnapshot.setData("Hello world".getBytes(StandardCharsets.UTF_8));

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