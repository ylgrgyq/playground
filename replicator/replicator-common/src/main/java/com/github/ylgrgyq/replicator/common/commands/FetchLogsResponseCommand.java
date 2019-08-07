package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@CommandFactoryManager.AutoLoad
public final class FetchLogsResponseCommand extends ResponseCommand {
    private static final byte VERSION = 1;

    private List<LogEntry> logs;

    public FetchLogsResponseCommand() {
        super(VERSION);
        this.logs = Collections.emptyList();
    }

    public List<LogEntry> getLogs() {
        return logs;
    }

    public void setLogs(List<LogEntry> logs) {
        if (logs != null) {
            this.logs = logs;
        }
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_LOGS;
    }

    @Override
    public void serialize() {
        List<byte[]> logsInBytes = logs.stream().map(LogEntry::serialize).collect(Collectors.toList());

        int size = logsInBytes.stream().mapToInt(bs -> bs.length).sum();
        byte[] buffer = new byte[Integer.BYTES * logs.size() + size];

        int off = 0;

        for (byte[] bs : logsInBytes) {
            int logSize = bs.length;
            Bits.putInt(buffer, off, logSize);
            off += 4;
            System.arraycopy(bs, 0, buffer, off, logSize);
            off += logSize;
        }

        setContent(buffer);
    }

    @Override
    public void deserialize(byte[] content) throws DeserializationException {
        ArrayList<LogEntry> entries = new ArrayList<>();
        if (content != null) {
            int off = 0;
            while (off < content.length) {
                int logSize = Bits.getInt(content, off);
                off += 4;

                if (content.length - off < logSize) {
                    throw new DeserializationException("Logs underflow");
                }

                byte[] bf = new byte[logSize];

                System.arraycopy(content, off, bf, 0, logSize);
                off += logSize;

                LogEntry e = new LogEntry();
                e.deserialize(bf);
                entries.add(e);
            }

            if (!entries.isEmpty()) {
                logs = entries;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FetchLogsResponseCommand that = (FetchLogsResponseCommand) o;
        return Objects.equals(getLogs(), that.getLogs());
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), getLogs());
    }

    @Override
    public String toString() {
        return "FetchLogsResponse{" +
                super.toString() +
                "logs=" + logs +
                '}';
    }
}
