package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RocksDbSequenceStorageTest {
    private Storage storage;
    private SequenceStorage sequenceStorage;
    private File testingDir;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "rocks_db_sequence_storage_test";
        testingDir = new File(tempDir);
        FileUtils.forceMkdir(testingDir);
        StorageOptions options = StorageOptions.builder()
                .setDestroyPreviousDbFiles(true)
                .setStoragePath(testingDir.getPath())
                .build();
        storage = new RocksDbStorage(options);

        sequenceStorage = storage.createSequenceStorage("testing_topic", SequenceOptions.builder().build());
    }

    @After
    public void tearDown() throws Exception {
        storage.shutdown();
        FileUtils.deleteDirectory(testingDir);
    }

    @Test
    public void append() {
        assertEquals(0, sequenceStorage.getFirstLogId());
        assertEquals(0, sequenceStorage.getLastLogId());

        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(1, sequenceStorage.getLastLogId());
        List<LogEntry> entries = sequenceStorage.getEntries(-1, 100);
        assertEquals(1, entries.size());
        LogEntry entry = entries.get(0);
        assertEquals(1, entry.getId());
        assertEquals("1", entry.getData().toStringUtf8());

        sequenceStorage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(2, sequenceStorage.getLastLogId());

        sequenceStorage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(3, sequenceStorage.getLastLogId());

        sequenceStorage.append(4, "4".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(4, sequenceStorage.getLastLogId());
    }

    @Test
    public void testDbRestart() throws InterruptedException {
        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(4, "4".getBytes(StandardCharsets.UTF_8));

        storage.shutdown();

        StorageOptions options = StorageOptions.builder()
                .setStoragePath(testingDir.getPath())
                .build();
        storage = new RocksDbStorage(options);

        sequenceStorage = storage.createSequenceStorage("testing_topic", SequenceOptions.builder().build());
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(4, sequenceStorage.getLastLogId());
        List<LogEntry> entries = sequenceStorage.getEntries(1, 100);
        for (int i = 1; i <= 4; i++) {
            assertEquals(i, entries.get(i - 1).getId());
            assertEquals("" + i, entries.get(i - 1).getData().toStringUtf8());
        }
    }

    @Test(expected = ReplicatorException.class)
    public void testAppendAfterDropSequenceStorage() {
        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(4, "4".getBytes(StandardCharsets.UTF_8));

        sequenceStorage.drop();

        sequenceStorage.append(5, "5".getBytes(StandardCharsets.UTF_8));
    }

    @Test()
    public void testDropSequenceStorage() {
        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(4, "4".getBytes(StandardCharsets.UTF_8));

        sequenceStorage.drop();

        sequenceStorage = storage.createSequenceStorage("testing_topic", SequenceOptions.builder().build());
        assertEquals(0, sequenceStorage.getFirstLogId());
        assertEquals(0, sequenceStorage.getLastLogId());
        List<LogEntry> entries = sequenceStorage.getEntries(1, 100);
        assertTrue(entries.isEmpty());

        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(2, "2".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(3, "3".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(4, "4".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, sequenceStorage.getFirstLogId());
        assertEquals(4, sequenceStorage.getLastLogId());
        assertEquals(4, sequenceStorage.getEntries(1, 100).size());
    }

    @Test
    public void getEntries() {
        int end = 5;
        int start = 1;
        for (int i = start; i < end; i++) {
            sequenceStorage.append(i, ("" + i).getBytes(StandardCharsets.UTF_8));
        }

        for (int from = -1; from < end; ++from) {
            for (int limit = 1; limit < end; ++limit) {
                List<LogEntry> entries = sequenceStorage.getEntries(from, limit);
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
        sequenceStorage.append(1, "1".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(5, "5".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(7, "7".getBytes(StandardCharsets.UTF_8));
        sequenceStorage.append(10, "10".getBytes(StandardCharsets.UTF_8));

        for (int i = -1; i < 15; ++i) {
            sequenceStorage.trimToId(i);
            if (i < 2) {
                assertEquals(1, sequenceStorage.getFirstLogId());
                assertEquals(10, sequenceStorage.getLastLogId());
            } else if (i < 6) {
                assertEquals(5, sequenceStorage.getFirstLogId());
                assertEquals(10, sequenceStorage.getLastLogId());
            } else if (i < 8) {
                assertEquals(7, sequenceStorage.getFirstLogId());
                assertEquals(10, sequenceStorage.getLastLogId());
            } else if (i < 11) {
                assertEquals(10, sequenceStorage.getFirstLogId());
                assertEquals(10, sequenceStorage.getLastLogId());
            } else {
                assertEquals(i, sequenceStorage.getFirstLogId());
            }
        }
    }


}