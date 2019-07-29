package com.github.ylgrgyq.replicator.server.connection.websocket;

import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
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
        resp.setType(ReplicatorCommand.CommandType.HANDSHAKE_RESP);

        logger.debug("send handshake resp");
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeSnapshot(Snapshot snapshot) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.SNAPSHOT_RESP);
        resp.setSnapshot(snapshot);
        logger.debug("send snapshot resp {}", resp);
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeSyncLog(SyncLogEntries log) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.GET_RESP);
        resp.setLogs(log);
        logger.debug("send get resp {} {}", resp, resp.build().toByteArray().length);
        socket.writeAndFlush(resp.build());
    }

    @Override
    public void writeError(ReplicatorError error) {
        ReplicatorCommand.Builder builder = ReplicatorCommand.newBuilder();
        builder.setType(ReplicatorCommand.CommandType.ERROR);

        ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
        errorInfo.setErrorCode(error.getErrorCode());
        errorInfo.setErrorMsg(error.getMsg());
        builder.setError(errorInfo);
        logger.debug("send error {}", errorInfo);
        socket.writeAndFlush(builder.build());
    }
}
