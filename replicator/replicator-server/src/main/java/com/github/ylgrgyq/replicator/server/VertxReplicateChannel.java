package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxReplicateChannel implements ReplicateChannel{
    private static final Logger logger = LoggerFactory.getLogger(VertxReplicateChannel.class);

    private ServerWebSocket socket;
    public VertxReplicateChannel(ServerWebSocket socket) {
        this.socket = socket;
    }

    @Override
    public void writeHandshakeResult() {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.HANDSHAKE_RESP);

        logger.info("send handshake resp");
        Buffer buf = Buffer.buffer(resp.build().toByteArray());
        socket.write(buf);
    }

    @Override
    public void writeSnapshot(Snapshot snapshot) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.SNAPSHOT_RESP);
        resp.setSnapshot(snapshot);
        logger.info("send snapshot resp {}", resp);
        Buffer buf = Buffer.buffer(resp.build().toByteArray());
        socket.write(buf);
    }

    @Override
    public void writeSyncLog(SyncLogEntries log) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.GET_RESP);
        resp.setLogs(log);
        logger.info("send get resp {} {}", resp, resp.build().toByteArray().length);
        Buffer buf = Buffer.buffer(resp.build().toByteArray());
        socket.writeBinaryMessage(buf);
    }

    @Override
    public void writeError(ReplicatorError error) {
        ReplicatorCommand.Builder builder = ReplicatorCommand.newBuilder();
        builder.setType(ReplicatorCommand.CommandType.ERROR);

        ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
        errorInfo.setErrorCode(error.getErrorCode());
        errorInfo.setErrorMsg(error.getMsg());
        builder.setError(errorInfo);
        logger.info("send error {}", errorInfo);
        Buffer buf = Buffer.buffer(builder.build().toByteArray());
        socket.writeBinaryMessage(buf);
    }
}
