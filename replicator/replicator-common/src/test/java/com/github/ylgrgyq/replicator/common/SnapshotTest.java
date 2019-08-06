package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class SnapshotTest {

    @Test
    public void serialize() throws Exception {
        long expectId = 1010101;
        byte[] expectData = "Hello".getBytes();
        Snapshot expectSnapshot = new Snapshot();
        expectSnapshot.setId(expectId);
        expectSnapshot.setData(expectData);

        byte[] bs = expectSnapshot.serialize();

        Snapshot actualSnapshot = new Snapshot();
        actualSnapshot.deserialize(bs);

        assertEquals(expectSnapshot, actualSnapshot);
        assertEquals(1010101, actualSnapshot.getId());
        assertArrayEquals(expectData, actualSnapshot.getData());
    }
}