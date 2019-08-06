package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.ReplicateChannel;
import com.github.ylgrgyq.replicator.common.ReplicatorError;
import com.github.ylgrgyq.replicator.common.entity.*;
import com.github.ylgrgyq.replicator.server.sequence.SequenceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Replica implements ReplicateRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(Replica.class);

    private AtomicBoolean handshaked;
    private ReplicateChannel channel;
    private SequenceImpl seq;

    public Replica(ReplicateChannel channel) {
        this.channel = channel;
        this.handshaked = new AtomicBoolean(false);
    }

    @Override
    public void onStart(ReplicatorRemotingContext ctx, SequenceImpl seq) {
        this.seq = seq;
        handshaked.set(true);

        HandshakeResponse handshake = new HandshakeResponse();

        ctx.sendResponse(handshake);
    }

    @Override
    public void handleFetchLogs(ReplicatorRemotingContext ctx, FetchLogsRequest fetchLogs) {
        if (!checkHandshakeState()) {
            return;
        }

        logger.debug("Got fetch logs request: {}", fetchLogs);

        long fromIndex = fetchLogs.getFromId();
        int limit = fetchLogs.getLimit();

        List<LogEntry> logs = seq.getLogs(fromIndex, limit);
        FetchLogsResponse r = new FetchLogsResponse();
        r.setLogs(logs);

        logger.debug("send get resp {} {}", r);
        ctx.sendResponse(r);
    }

    @Override
    public void handleFetchSnapshot(ReplicatorRemotingContext ctx) {
        if (!checkHandshakeState()) {
            return;
        }

        logger.info("Got fetch snapshot request");

        Snapshot snapshot = seq.getLastSnapshot();
        FetchSnapshotResponse r = new FetchSnapshotResponse();
        r.setSnapshot(snapshot);

        logger.debug("send snapshot resp {}", r);

        ctx.sendResponse(r);
    }

    @Override
    public void onFinish() {
        channel.close();
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
