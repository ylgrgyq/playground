package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@CommandFactoryManager.AutoLoad
public final class FetchLogsResponseCommand extends ResponseCommandV1 {
    private List<LogEntry> logs;

    public FetchLogsResponseCommand() {
        super(MessageType.FETCH_LOGS);
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
    public void serialize() {
        int size = logs.stream().mapToInt(LogEntry::getSerializedSize).sum();
        byte[] buffer = new byte[Integer.BYTES * logs.size() + size];

        int off = 0;
        for (LogEntry e : logs) {
            int logSize = e.getSerializedSize();
            Bits.putInt(buffer, off, logSize);
            off += 4;
            System.arraycopy(e.toByteArray(), 0, buffer, off, logSize);
            off += logSize;
        }

        setContent(buffer);
    }

    @Override
    public void deserialize() throws DeserializationException {
        byte[] content = getContent();

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

                try {
                    entries.add(LogEntry.parseFrom(bf));
                } catch (InvalidProtocolBufferException ex) {
                    throw new DeserializationException();
                }
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
