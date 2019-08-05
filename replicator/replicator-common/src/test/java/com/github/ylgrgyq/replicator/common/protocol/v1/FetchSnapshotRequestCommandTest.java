package com.github.ylgrgyq.replicator.common.protocol.v1;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FetchSnapshotRequestCommandTest {
    @Test
    public void serialize() {
        FetchSnapshotRequestCommand expect = new FetchSnapshotRequestCommand();

        expect.serialize();

        FetchSnapshotRequestCommand actual = new FetchSnapshotRequestCommand();
        actual.setContent(expect.getContent());
        actual.deserialize();

        assertEquals(expect, actual);
    }
}