package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.common.MessageType;
import com.github.ylgrgyq.replicator.proto.*;
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
    public void onStart(String topic, Sequence seq, RemotingCommand cmd) {
        ResponseCommand res = CommandFactory.createResponse((RequestCommand)cmd);
        this.topic = topic;
        this.seq = seq;
        handshaked.set(true);

        HandshakeResponse handshake = HandshakeResponse.newBuilder().build();
        res.setResponseObject(handshake);

        channel.writeRemoting(res);
    }

    @Override
    public void handleFetchLogs(RemotingCommand cmd) {
        if (!checkHandshakeState()) {
            return;
        }

        FetchLogsRequest fetchLogs = cmd.getBody();

        logger.debug("Got fetch logs request: {}", fetchLogs);

        long fromIndex = fetchLogs.getFromId();
        int limit = fetchLogs.getLimit();

        BatchLogEntries log = seq.getLogs(fromIndex, limit);

        ResponseCommand res = CommandFactory.createResponse((RequestCommand) cmd);
        res.setMessageType(MessageType.FETCH_LOGS);

        FetchLogsResponse r = FetchLogsResponse.newBuilder()
                .setLogs(log)
                .build();

        res.setContent(r.toByteArray());

        logger.debug("send get resp {} {}", r);
        channel.writeRemoting(res);
    }

    @Override
    public void handleFetchSnapshot(RemotingCommand cmd) {
        if (!checkHandshakeState()) {
            return;
        }

        logger.info("Got fetch snapshot request");

        Snapshot snapshot = seq.getLastSnapshot();

        FetchSnapshotResponse r = FetchSnapshotResponse.newBuilder()
                .setSnapshot(snapshot)
                .build();

        ResponseCommand res = CommandFactory.createResponse((RequestCommand)cmd);
        res.setContent(r.toByteArray());

        logger.debug("send snapshot resp {}", r);


        channel.writeRemoting(res);
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
