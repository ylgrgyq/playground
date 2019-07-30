package com.github.ylgrgyq.replicator.server.connection.websocket;

import com.github.ylgrgyq.replicator.proto.*;
import com.github.ylgrgyq.replicator.server.ReplicateChannel;
import com.github.ylgrgyq.replicator.server.ReplicatorError;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyReplicateChannel implements ReplicateChannel {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

    private Channel socket;
    public NettyReplicateChannel(Channel socket) {
        this.socket = socket;
    }

    @Override
    public void writeHandshakeResult() {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(CommandType.HANDSHAKE);
        resp.setMsgType(MessageType.RESPONSE);

        logger.debug("send handshake resp");
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeSnapshot(Snapshot snapshot) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(CommandType.FETCH_SNAPSHOT);
        resp.setMsgType(MessageType.RESPONSE);

        FetchSnapshotResponse r = FetchSnapshotResponse.newBuilder()
                .setSnapshot(snapshot)
                .build();
        resp.setFetchSnapshotResponse(r);

        logger.debug("send snapshot resp {}", resp);
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeSyncLog(BatchLogEntries log) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(CommandType.FETCH_LOGS);
        resp.setMsgType(MessageType.RESPONSE);

        FetchLogsResponse r = FetchLogsResponse.newBuilder()
                .setLogs(log)
                .build();
        resp.setFetchLogsResponse(r);
        logger.debug("send get resp {} {}", resp, resp.build().toByteArray().length);
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeError(ReplicatorError error) {
        ReplicatorCommand.Builder builder = ReplicatorCommand.newBuilder();
        builder.setType(CommandType.ERROR);
        builder.setMsgType(MessageType.ONE_WAY);

        ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
        errorInfo.setErrorCode(error.getErrorCode());
        errorInfo.setErrorMsg(error.getMsg());
        builder.setError(errorInfo);
        logger.debug("send error {}", errorInfo);
        socket.writeAndFlush(builder.build());
    }
}
