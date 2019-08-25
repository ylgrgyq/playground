package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.FileUtils;
import com.github.ylgrgyq.reservoir.StorageException;
import com.github.ylgrgyq.reservoir.TestingUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LogTest {
    private String tempLogFile;
    private LogWriter logWriter;
    private LogReader logReader;
    private FileChannel writeChannel;
    private FileChannel readChannel;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                "reservoir_log_test_" + System.nanoTime();
        FileUtils.forceMkdir(new File(tempDir));
        this.tempLogFile = tempDir + File.separator + "log_test";

        writeChannel = FileChannel.open(Paths.get(tempLogFile), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        readChannel = FileChannel.open(Paths.get(tempLogFile),
                StandardOpenOption.READ);

        logWriter = new LogWriter(writeChannel);
        logReader = new LogReader(readChannel, 0, true);
    }

    @After
    public void tearDown() throws Exception {
        logWriter.close();
        logReader.close();
    }

    @Test
    public void simpleReadWriteLog() throws Exception {
        writeLog("Hello");
        writeLog("World");
        writeLog("!");

        assertThat(readLog()).isEqualTo("Hello");
        assertThat(readLog()).isEqualTo("World");
        assertThat(readLog()).isEqualTo("!");
        assertThat(readLog()).isEqualTo("EOF");
        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void simpleReadWriteLotsLog() throws Exception {
        for (int i = 0; i < 10000; i++) {
            writeLog(TestingUtils.numberString(i));
        }

        for (int i = 0; i < 10000; i++) {
            assertThat(readLog()).isEqualTo(TestingUtils.numberString(i));
        }

        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void simpleReadWriteRandomLengthLog() throws Exception {
        Random random = new Random(101);

        // we must have a bound to ensure test will not OOM
        for (int i = 0; i < 100; i++) {
            writeLog(constructTestingLog("Hello", nextPositiveInt(random, 2 * Constant.kBlockSize)));
        }

        random = new Random(101);
        for (int i = 0; i < 100; i++) {
            assertThat(readLog()).isEqualTo(constructTestingLog("Hello", nextPositiveInt(random, 2 * Constant.kBlockSize)));
        }

        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void simpleReadWriteFragmentedLog() throws Exception {
        writeLog("Small");
        writeLog(constructTestingLog("HalfBlock", Constant.kBlockSize / 2));
        writeLog(constructTestingLog("WholeBlock", Constant.kBlockSize));
        writeLog(constructTestingLog("MegaBlock", 100 * Constant.kBlockSize));

        assertThat(readLog()).isEqualTo("Small");
        assertThat(readLog()).isEqualTo(constructTestingLog("HalfBlock", Constant.kBlockSize / 2));
        assertThat(readLog()).isEqualTo(constructTestingLog("WholeBlock", Constant.kBlockSize));
        assertThat(readLog()).isEqualTo(constructTestingLog("MegaBlock", 100 * Constant.kBlockSize));

        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void readWriteTrailer() throws Exception {
        // write a log which only leave a header size space in a block
        writeLog(constructTestingLog("Hello", Constant.kBlockSize - 2 * Constant.kHeaderSize));
        assertThat(writeChannel.size()).isEqualTo(Constant.kBlockSize - Constant.kHeaderSize);
        // write a new log which will be write to a new block
        writeLog("World");

        assertThat(readLog()).isEqualTo(constructTestingLog("Hello", Constant.kBlockSize - 2 * Constant.kHeaderSize));
        assertThat(readLog()).isEqualTo("World");

        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void paddingBlock() throws Exception {
        // write a log which leaves space in block shorter than the header size
        writeLog(constructTestingLog("Hello", Constant.kBlockSize - 2 * Constant.kHeaderSize + 6));
        assertThat(writeChannel.size()).isEqualTo(Constant.kBlockSize - Constant.kHeaderSize + 6);
        // write a new log which will be write to a new block
        writeLog("World");

        assertThat(readLog()).isEqualTo(constructTestingLog("Hello", Constant.kBlockSize - 2 * Constant.kHeaderSize + 6));
        assertThat(readLog()).isEqualTo("World");

        assertThat(readLog()).isEqualTo("EOF");
    }

    @Test
    public void testReopenWriter() throws Exception {
        writeLog("Hello");
        reopenWriter();
        writeLog("World");
        writeLog("!");

        assertThat(readLog()).isEqualTo("Hello");
        assertThat(readLog()).isEqualTo("World");
        assertThat(readLog()).isEqualTo("!");
        assertThat(readLog()).isEqualTo("EOF");
        assertThat(readLog()).isEqualTo("EOF");
    }

//    @Test
//    public void testReadFailed() throws Exception {
//        writeLog("Hello");
//
//        readChannel.close();
//
//
//
//        assertThat(readLog()).isEqualTo("Hello");
//
//        assertThat(readLog()).isEqualTo("EOF");
//    }


    private void writeLog(String log) throws IOException {
        logWriter.append(log.getBytes(StandardCharsets.UTF_8));
        logWriter.flush();
    }

    private String readLog() throws IOException, StorageException {
        List<byte[]> logs = logReader.readLog();
        if (logs.isEmpty()) {
            return "EOF";
        } else {
            return new String(concatByteArray(logs), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private int nextPositiveInt(Random random, int bound) {
        int nextInt;
        while ((nextInt = random.nextInt(bound)) <= 0) {
            // loop
        }
        return nextInt;
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

    private String constructTestingLog(String base, int expectSize) {
        final int baseInNeed = expectSize / base.length();
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < baseInNeed; i++) {
            builder.append(base);
        }

        builder.append(base, 0, expectSize - baseInNeed * base.length());
        return builder.toString();
    }

    private void reopenWriter() throws Exception {
        logWriter.close();
        writeChannel = FileChannel.open(Paths.get(tempLogFile), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        logWriter = new LogWriter(writeChannel);
    }
}