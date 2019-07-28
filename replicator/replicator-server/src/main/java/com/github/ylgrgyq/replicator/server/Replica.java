package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.github.ylgrgyq.replicator.server.sequence.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class Replica implements ReplicateRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(Replica.class);

    private AtomicBoolean handshaked;
    private ReplicateChannel channel;
    private Sequence seq;
    private String topic;

    public Replica(ReplicateChannel channel) {
        this.channel = channel;
        this.handshaked = new AtomicBoolean(false);
    }

    @Override
    public void onStart(String topic, Sequence seq) {
        this.topic = topic;
        this.seq = seq;
        handshaked.set(true);
        channel.writeHandshakeResult();
    }

    @Override
    public void heandleSyncLogs(long fromIndex, int limit) {
        if (!checkHandshakeState()) {
            return;
        }

        logger.info("sync {} {} {} {}", topic, seq, fromIndex, limit);
        SyncLogEntries log = seq.syncLogs(fromIndex, limit);

        channel.writeSyncLog(log);
    }

    @Override
    public void handleSyncSnapshot() {
        if (!checkHandshakeState()) {
            return;
        }

        Snapshot snapshot = seq.getLastSnapshot();
        channel.writeSnapshot(snapshot);
    }

    @Override
    public void onFinish() {

    }

    private boolean checkHandshakeState() {
        if (handshaked.get()) {
            return true;
        } else {
            channel.writeError(ReplicatorError.ENEEDHAND_SHAKE);
            return false;
        }
    }
}
