package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RocksDbStorageTest {
    private Storage storage;
    private File testingDir;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_server_test_" + System.nanoTime();
        testingDir = new File(tempDir);
        FileUtils.forceMkdir(testingDir);
        storage = new RocksDbStorage(testingDir.getPath());
        storage.init();
    }

    @After
    public void tearDown() throws Exception {
        storage.shutdown();
        FileUtils.deleteDirectory(testingDir);
    }

    @Test
    public void append() {
        assertEquals(0, storage.getFirstLogId());
        assertEquals(0, storage.getLastLogId());

        storage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, storage.getFirstLogId());
        assertEquals(1, storage.getLastLogId());
        List<LogEntry> entries = storage.getEntries(-1, 100);
        assertEquals(1, entries.size());
        LogEntry entry = entries.get(0);
        assertEquals(1, entry.getId());
        assertEquals("1", entry.getData().toStringUtf8());

        storage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, storage.getFirstLogId());
        assertEquals(2, storage.getLastLogId());

        storage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, storage.getFirstLogId());
        assertEquals(3, storage.getLastLogId());

        storage.append(4, "4".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, storage.getFirstLogId());
        assertEquals(4, storage.getLastLogId());
    }

    @Test
    public void getEntries() {
        int end = 5;
        int start = 1;
        for (int i = start; i < end; i++) {
            storage.append(i, ("" + i).getBytes(StandardCharsets.UTF_8));
        }

        for (int from = -1; from < end; ++from) {
            for (int limit = 1; limit < end; ++limit) {
                List<LogEntry> entries = storage.getEntries(from, limit);
                assertEquals(Math.min(limit, end - from), entries.size());
                for (int i = 0; i < entries.size(); i++) {
                    String strInEntry = new String(entries.get(i).getData().toByteArray(), StandardCharsets.UTF_8);
                    assertEquals("" + (Math.max(from, start) + i), strInEntry);
                }
            }
        }
    }

    @Test
    public void trimToId() {
        storage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        storage.append(5, "5".getBytes(StandardCharsets.UTF_8));
        storage.append(7, "7".getBytes(StandardCharsets.UTF_8));
        storage.append(10, "10".getBytes(StandardCharsets.UTF_8));

        for (int i = -1; i < 15; ++i) {
            storage.trimToId(i);
            if (i < 2) {
                assertEquals(1, storage.getFirstLogId());
                assertEquals(10, storage.getLastLogId());
            } else if (i < 6) {
                assertEquals(5, storage.getFirstLogId());
                assertEquals(10, storage.getLastLogId());
            } else if (i < 8) {
                assertEquals(7, storage.getFirstLogId());
                assertEquals(10, storage.getLastLogId());
            } else if (i < 11) {
                assertEquals(10, storage.getFirstLogId());
                assertEquals(10, storage.getLastLogId());
            } else {
                assertEquals(i, storage.getFirstLogId());
            }
        }
    }
}