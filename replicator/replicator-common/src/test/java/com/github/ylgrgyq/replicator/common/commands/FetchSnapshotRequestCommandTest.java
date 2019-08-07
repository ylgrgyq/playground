package com.github.ylgrgyq.replicator.common.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FetchSnapshotRequestCommandTest {
    @Test
    public void serialize() {
        FetchSnapshotRequestCommand expect = new FetchSnapshotRequestCommand();

        expect.serialize();

        FetchSnapshotRequestCommand actual = new FetchSnapshotRequestCommand();
        actual.deserialize(expect.getContent());

        assertEquals(expect, actual);
    }
}