package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.FileUtils;
import com.github.ylgrgyq.reservoir.StorageException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ManifestTest {
    private File testingBaseDir;
    private Manifest manifest;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "reservoir_test_" + System.nanoTime();
        testingBaseDir = new File(tempDir);
        FileUtils.forceMkdir(testingBaseDir);
        manifest = new Manifest(testingBaseDir.getPath());
    }

    @After
    public void tearDown() throws Exception {
        manifest.close();
    }

    @Test
    public void testGetFirstId() throws Exception {
        assertThat(manifest.getFirstId()).isEqualTo(-1);

        for (int i = 2; i < 100; i++) {
            final ManifestRecord record = ManifestRecord.newPlainRecord();
            final SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
            meta.setFirstId(i);
            record.addMeta(meta);
            manifest.logRecord(record);

            assertThat(manifest.getFirstId()).isEqualTo(2);
        }
    }

    @Test
    public void testGetLastId() throws Exception {
        assertThat(manifest.getLastId()).isEqualTo(-1);

        for (int i = 2; i < 100; i++) {
            final ManifestRecord record = ManifestRecord.newPlainRecord();
            final SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
            meta.setLastId(i);
            record.addMeta(meta);
            manifest.logRecord(record);

            assertThat(manifest.getLastId()).isEqualTo(i);
        }
    }

    @Test
    public void testGetNextFileNumber() {
        assertThat(manifest.getNextFileNumber()).isEqualTo(1);
        for (int i = 2; i < 10000; i++) {
            assertThat(manifest.getNextFileNumber()).isEqualTo(i);
        }
    }

    @Test
    public void testGetDataLogNumber() throws Exception {
        assertThat(manifest.getDataLogFileNumber()).isEqualTo(0);

        for (int i = 2; i < 100; i++) {
            final ManifestRecord record = ManifestRecord.newPlainRecord();
            record.setDataLogFileNumber(i);
            manifest.logRecord(record);

            assertThat(manifest.getDataLogFileNumber()).isEqualTo(i);
        }
    }


    @Test
    public void testRecoverFromNonExistManifest() {
        String tempDir = "recover_from_non_exists_" + System.nanoTime();
        File tempFile = new File(tempDir);
        assertThatThrownBy(() -> manifest.recover(tempFile.getPath()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("CURRENT file points to an non-exists manifest file");
    }

    @Test
    public void testRecover() throws Exception {
        final ManifestRecord emptyMetaRecord = ManifestRecord.newPlainRecord();
        emptyMetaRecord.setDataLogFileNumber(2);
        emptyMetaRecord.setNextFileNumber(2);
        manifest.logRecord(emptyMetaRecord);

        ManifestRecord record = ManifestRecord.newPlainRecord();
        record.setDataLogFileNumber(3);
        record.setNextFileNumber(3);
        SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
        meta.setLastId(3);
        meta.setFirstId(3);
        meta.setFileSize(1010);
        meta.setFileNumber(3);
        record.addMeta(meta);
        manifest.logRecord(record);

        record = ManifestRecord.newPlainRecord();
        record.setDataLogFileNumber(4);
        record.setNextFileNumber(4);
        meta = new SSTableFileMetaInfo();
        meta.setLastId(4);
        meta.setFirstId(4);
        meta.setFileSize(10101);
        meta.setFileNumber(4);
        record.addMeta(meta);
        manifest.logRecord(record);

        Manifest newManifest = new Manifest(testingBaseDir.getPath());
        newManifest.recover(getCurrentManifestFileName());

        assertThat(newManifest).isEqualTo(manifest);
    }

    @Test
    public void testRecoverWithReplaceMetasRecord() throws Exception {
        final ManifestRecord emptyMetaRecord = ManifestRecord.newPlainRecord();
        emptyMetaRecord.setDataLogFileNumber(2);
        emptyMetaRecord.setNextFileNumber(2);
        manifest.logRecord(emptyMetaRecord);

        ManifestRecord record = ManifestRecord.newReplaceAllExistedMetasRecord();
        record.setDataLogFileNumber(3);
        manifest.logRecord(record);

        record = ManifestRecord.newPlainRecord();
        record.setDataLogFileNumber(4);
        record.setNextFileNumber(4);
        SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
        meta.setLastId(4);
        meta.setFirstId(4);
        meta.setFileSize(10101);
        meta.setFileNumber(4);
        record.addMeta(meta);
        manifest.logRecord(record);

        Manifest newManifest = new Manifest(testingBaseDir.getPath());
        newManifest.recover(getCurrentManifestFileName());

        assertThat(newManifest).isEqualTo(manifest);
    }

    private String getCurrentManifestFileName() throws IOException {
        final Path currentFilePath = Paths.get(testingBaseDir.getPath(), FileName.getCurrentFileName());
        return new String(Files.readAllBytes(currentFilePath), StandardCharsets.UTF_8);
    }

}