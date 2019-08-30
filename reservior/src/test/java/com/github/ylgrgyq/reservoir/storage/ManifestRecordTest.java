package com.github.ylgrgyq.reservoir.storage;

import org.assertj.core.util.Lists;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class ManifestRecordTest {
    @Test
    public void testEncodeEmptyMetas() {
        final ManifestRecord record = new ManifestRecord();
        record.setDataLogFileNumber(101);
        record.setNextFileNumber(102);

        assertThat(ManifestRecord.decode(Lists.list(record.encode()))).isEqualTo(record);
    }

    @Test
    public void testEncodeSomeMetas() {
        final ManifestRecord record = new ManifestRecord();
        record.setDataLogFileNumber(101);
        record.setNextFileNumber(102);

        for (int i = 0; i < 1000; i++) {
            final SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
            meta.setFileNumber(i);
            meta.setFileSize(i);
            meta.setFirstKey(i);
            meta.setLastKey(i);
            record.addMeta(meta);
        }

        assertThat(ManifestRecord.decode(Lists.list(record.encode()))).isEqualTo(record);
    }

}