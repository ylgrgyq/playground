package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SnapshotManagerTest {
    private ReplicatorClientOptions options;
    private String storagePath;
    private SnapshotManager manager;

    @Before
    public void setUp() throws Exception {
        storagePath = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_client_test_" + System.nanoTime();

        options = ReplicatorClientOptions.builder()
                .setSnapshotStoragePath(storagePath)
                .setMaxSnapshotsToKeep(10)
                .setUri(new URI("ws://localhost:8888"))
                .build();

        manager = new SnapshotManager(options);
    }

    @Test
    public void storeSnapshot() throws Exception {
        Snapshot snapshot = new Snapshot();
        snapshot.setData("Hello".getBytes(StandardCharsets.UTF_8));
        snapshot.setId(100);


        assertNull(manager.getLastSnapshot());
        manager.storeSnapshot(snapshot);

        snapshot = new Snapshot();
        snapshot.setData("Hello".getBytes(StandardCharsets.UTF_8));
        snapshot.setId(101);
        manager.storeSnapshot(snapshot);
        assertEquals(snapshot, manager.getLastSnapshot());
    }

    @Test
    public void loadLastSnapshot() throws Exception {
        Snapshot snapshot = new Snapshot();
        snapshot.setData("Hello".getBytes(StandardCharsets.UTF_8));
        snapshot.setId(100);
        manager.storeSnapshot(snapshot);

        snapshot = new Snapshot();
        snapshot.setData("Big".getBytes(StandardCharsets.UTF_8));
        snapshot.setId(101);
        manager.storeSnapshot(snapshot);

        snapshot = new Snapshot();
        snapshot.setData("World".getBytes(StandardCharsets.UTF_8));
        snapshot.setId(102);
        manager.storeSnapshot(snapshot);

        assertEquals(snapshot, manager.getLastSnapshot());

        manager = new SnapshotManager(options);
        assertEquals(snapshot, manager.getLastSnapshot());
    }

    @Test
    public void purgeOldSnapshotOnLoad() throws Exception {
        List<Snapshot> storedSnapshot = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            Snapshot snapshot = new Snapshot();
            snapshot.setData(("Hello-" + i).getBytes(StandardCharsets.UTF_8));
            snapshot.setId(100 + i);
            manager.storeSnapshot(snapshot);
            storedSnapshot.add(snapshot);
        }
        List<Snapshot> expectSortedSnapshot = storedSnapshot.subList(0, storedSnapshot.size());
        Collections.reverse(expectSortedSnapshot);
        expectSortedSnapshot = expectSortedSnapshot.subList(0, 5);

        assertEquals(expectSortedSnapshot.get(0), manager.getLastSnapshot());

        ReplicatorClientOptions options = ReplicatorClientOptions.builder()
                .setSnapshotStoragePath(storagePath)
                .setMaxSnapshotsToKeep(5)
                .setUri(new URI("ws://localhost:8888"))
                .build();
        manager = new SnapshotManager(options);
        assertEquals(expectSortedSnapshot.get(0), manager.getLastSnapshot());

        List<Path> actualSnapshots = manager.listAllAvailableSnapshotPath(true);
        while (true) {
            if (actualSnapshots.size() != expectSortedSnapshot.size()) {
                Thread.sleep(100);
            } else {
                break;
            }
        }

        for (int i = 0; i < expectSortedSnapshot.size(); i++) {
            Snapshot expect = expectSortedSnapshot.get(i);
            Snapshot actual = manager.loadSnapshotOnPath(actualSnapshots.get(i));
            assertEquals(expect, actual);
        }
    }

    @Test
    public void purgeOldSnapshotOnStore() throws Exception {
        List<Snapshot> storedSnapshot = new ArrayList<>(10);
        for (int i = 0; i < 20; i++) {
            Snapshot snapshot = new Snapshot();
            snapshot.setData(("Hello-" + i).getBytes(StandardCharsets.UTF_8));
            snapshot.setId(100 + i);
            manager.storeSnapshot(snapshot);
            storedSnapshot.add(snapshot);
        }
        List<Snapshot> expectSortedSnapshot = storedSnapshot.subList(0, storedSnapshot.size());
        Collections.reverse(expectSortedSnapshot);
        expectSortedSnapshot = expectSortedSnapshot.subList(0, 10);

        assertEquals(expectSortedSnapshot.get(0), manager.getLastSnapshot());

        List<Path> actualSnapshots = manager.listAllAvailableSnapshotPath(true);
        while (true) {
            if (actualSnapshots.size() != expectSortedSnapshot.size()) {
                Thread.sleep(100);
            } else {
                break;
            }
        }

        for (int i = 0; i < expectSortedSnapshot.size(); i++) {
            Snapshot expect = expectSortedSnapshot.get(i);
            Snapshot actual = manager.loadSnapshotOnPath(actualSnapshots.get(i));
            assertEquals(expect, actual);
        }
    }
}