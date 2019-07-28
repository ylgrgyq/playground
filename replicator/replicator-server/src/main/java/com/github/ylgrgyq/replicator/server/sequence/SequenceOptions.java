package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.server.SnapshotGenerator;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SequenceOptions {
    private final SnapshotGenerator snapshotGenerator;
    private final long generateSnapshotIntervalSecs;

    private SequenceOptions(SequenceOptionsBuilder builder) {
        this.generateSnapshotIntervalSecs = builder.generateSnapshotIntervalSecs == null ? 10 : builder.generateSnapshotIntervalSecs;
        this.snapshotGenerator = builder.snapshotGenerator;
    }

    public SnapshotGenerator getSnapshotGenerator() {
        return snapshotGenerator;
    }


    public long getGenerateSnapshotIntervalSecs() {
        return generateSnapshotIntervalSecs;
    }

    public static SequenceOptionsBuilder builder() {
        return new SequenceOptionsBuilder();
    }

    public static class SequenceOptionsBuilder {
        private Long generateSnapshotIntervalSecs;
        private SnapshotGenerator snapshotGenerator;

        public SequenceOptionsBuilder setGenerateSnapshotInterval(long generateSnapshotInterval, TimeUnit unit) {
            this.generateSnapshotIntervalSecs = unit.toSeconds(generateSnapshotInterval);
            return this;
        }

        public SequenceOptionsBuilder setSnapshotGenerator(SnapshotGenerator snapshotGenerator) {
            Objects.requireNonNull(snapshotGenerator);

            this.snapshotGenerator = snapshotGenerator;
            return this;
        }

        public SequenceOptions build() {
            return new SequenceOptions(this);
        }
    }
}
