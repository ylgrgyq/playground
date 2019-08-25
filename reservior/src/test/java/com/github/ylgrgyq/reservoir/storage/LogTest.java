package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.FileUtils;
import com.github.ylgrgyq.reservoir.StorageException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LogTest {
    private String tempLogFile;
    private LogWriter logWriter;
    private LogReader logReader;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                "reservoir_log_test_" + System.nanoTime();
        System.out.println(System.getProperty("java.io.tmpdir", "/tmp"));
        FileUtils.forceMkdir(new File(tempDir));
        this.tempLogFile = tempDir + File.separator + "log_test";

        FileChannel writeChannel = FileChannel.open(Paths.get(tempLogFile), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileChannel readChannel = FileChannel.open(Paths.get(tempLogFile),
                StandardOpenOption.READ);

        logWriter = new LogWriter(writeChannel);
        logReader = new LogReader(readChannel, 0, true);
    }

    @Test
    public void simpleReadWriteLog() throws Exception {
        writeLog("Hello");
        writeLog("World");
        writeLog("!");

        assertThat(readLog()).isEqualTo("Hello");
        assertThat(readLog()).isEqualTo("World");
        assertThat(readLog()).isEqualTo("!");
        assertThat(readLog()).isEqualTo("");
    }

    private void writeLog(String log) throws IOException {
        logWriter.append(log.getBytes(StandardCharsets.UTF_8));
        logWriter.flush();
    }

    private String readLog() throws IOException, StorageException {
        return new String(concatByteArray(logReader.readLog()), StandardCharsets.UTF_8);
    }

    private byte[] concatByteArray(List<byte[]> out) {
        final byte[] ret = new byte[out.stream().mapToInt(bs -> bs.length).sum()];
        int len = 0;
        for (byte[] bs : out) {
            System.arraycopy(bs, 0, ret, len, bs.length);
            len += bs.length;
        }
        return ret;
    }
}