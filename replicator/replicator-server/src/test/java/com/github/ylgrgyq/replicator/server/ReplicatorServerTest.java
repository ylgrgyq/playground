package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceReader;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReplicatorServerTest {
    private ReplicatorServer server;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_server_test_" + System.nanoTime();
        File tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);

        StorageOptions storageOptions = StorageOptions.builder()
                .setStoragePath(tempFile.getPath())
                .setDestroyPreviousDbFiles(true)
                .build();

        ReplicatorServerOptions options = ReplicatorServerOptions.builder()
                .setPort(8888)
                .setStorageOptions(storageOptions)
                .build();

        server = new ReplicatorServer(options);
        server.start().join();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testAppend() {
        String topic = "append_test";
        SequenceOptions sequenceOptions = SequenceOptions.builder()
                .setSnapshotGenerator(() -> {
                            Snapshot snapshot = new Snapshot();
                            snapshot.setId(100);
                            return snapshot;
                        }
                )
                .build();
        SequenceAppender appender = server.createSequence(topic, sequenceOptions);
        for (int i = 1; i < 1000L; ++i) {
            String msg = "log-" + i;
            appender.append(i, msg.getBytes(StandardCharsets.UTF_8));
        }

        SequenceReader reader = server.getSequenceReader(topic);
        List<LogEntry> logs = reader.getLogs(0, 100);
        assertEquals(100, logs.size());
    }
}