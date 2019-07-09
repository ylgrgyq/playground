package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class MemoryStorageTest {

    @Test
    public void append() {
        MemoryStorage storage = new MemoryStorage("topic");
        assertEquals(-1, storage.getFirstIndex());
        assertEquals(-1, storage.getLastIndex());

        storage.append(0, "1".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, storage.getFirstIndex());
        assertEquals(0, storage.getLastIndex());

        storage.append(1, "2".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, storage.getFirstIndex());
        assertEquals(1, storage.getLastIndex());

        storage.append(2, "3".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, storage.getFirstIndex());
        assertEquals(2, storage.getLastIndex());

        storage.append(3, "4".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, storage.getFirstIndex());
        assertEquals(3, storage.getLastIndex());
    }

    @Test
    public void getEntries() {
        MemoryStorage storage = new MemoryStorage("topic");
        int end = 4;
        int start = 0;
        for (int i = start; i < end; i++) {
            storage.append(i, ("" + i).getBytes(StandardCharsets.UTF_8));
        }

        for (int from = -1; from < end; ++from) {
            for (int limit = 1; limit < end; ++limit) {
                List<LogEntry> entries = storage.getEntries(from, limit);
                assertEquals(Math.min(limit, end - from - 1), entries.size());
                for (int i = 0; i < entries.size(); i++) {
                    String strInEntry = new String(entries.get(i).getData().toByteArray(), StandardCharsets.UTF_8);
                    assertEquals("" + (from + 1 + i), strInEntry);
                }
            }
        }
    }

    @Test
    public void trimToIndex() {
        for (int i = -1; i < 15; ++i) {
            MemoryStorage storage = new MemoryStorage("topic");
            storage.append(0, "1".getBytes(StandardCharsets.UTF_8));
            storage.append(5, "2".getBytes(StandardCharsets.UTF_8));
            storage.append(7, "3".getBytes(StandardCharsets.UTF_8));
            storage.append(10, "4".getBytes(StandardCharsets.UTF_8));
            storage.trimToId(i);
            if (i < 0) {
                assertEquals(0, storage.getFirstIndex());
                assertEquals(3, storage.getLastIndex());
            } else if (i < 5) {
                assertEquals(1, storage.getFirstIndex());
                assertEquals(3, storage.getLastIndex());
            } else if (i < 7) {
                assertEquals(2, storage.getFirstIndex());
                assertEquals(3, storage.getLastIndex());
            } else {
                assertEquals(3, storage.getFirstIndex());
                assertEquals(3, storage.getLastIndex());
            }
        }
    }
}