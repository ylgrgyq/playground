package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ReplicatorServer extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    @Override
    public void start(Future<Void> startFuture) {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost("localhost");

        HttpServer server = vertx.createHttpServer(options);

        SequenceGroups groups = new SequenceGroups();

        server.websocketHandler(socket ->
                socket.binaryMessageHandler(buffer -> {
                    ReplicatorCommand cmd;
                    try {
                        cmd = ReplicatorCommand.parseFrom(buffer.getBytes());
                    } catch (InvalidProtocolBufferException ex) {
                        writeError(socket, ReplicatorError.EUNKNOWNPROTOCOL);
                        return;
                    }

                    String topic = cmd.getTopic();
                    Sequence seq = groups.getSequence(topic);
                    if (seq == null) {
                        seq = groups.createSequence(topic, new SequenceOptions());
                    }
                    try {
                        switch (cmd.getType()) {
                            case GET:
                                long fromIndex = cmd.getFromIndex();
                                int limit = cmd.getLimit();

                                logger.info("sync {} {} {} {}", topic, seq, fromIndex, limit);
                                SyncLogEntries log = seq.syncLogs(fromIndex, limit);

                                writeSyncLog(socket, log);
                                break;
                            case SNAPSHOT:
                                Snapshot snapshot = seq.getSnapshot();
                                writeSnapshot(socket, snapshot);
                                break;
                        }
                    } catch (ReplicatorException ex) {
                        writeError(socket, ex.getError());
                    }
                })
        );

        server.exceptionHandler(t -> logger.error("Receive unexpected error", t));

        server.listen(8888, ret -> {
            if (ret.succeeded()) {
                startFuture.complete();

                SequenceOptions op = new SequenceOptions();
                op.setSequenceExecutor(Executors.newSingleThreadExecutor());
                Sequence seq = groups.createSequence("hahaha", op);
                for (int i = 0; i < 10000; ++i) {
                    String msg = "wahaha-" + i;
                    seq.append(i, msg.getBytes(StandardCharsets.UTF_8));
                }
                logger.info("generate log done {}", seq);
            } else {
                startFuture.fail(ret.cause());
            }
        });
    }

    private void writeSnapshot(ServerWebSocket socket, Snapshot snapshot) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.SNAPSHOT_RESP);
        resp.setSnapshot(snapshot);
        logger.info("send snapshot resp {}", resp);
        Buffer buf = Buffer.buffer(resp.build().toByteArray());
        socket.write(buf);
    }

    private void writeSyncLog(ServerWebSocket socket, SyncLogEntries log) {
        ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
        resp.setType(ReplicatorCommand.CommandType.GET_RESP);
        resp.setLogs(log);
        logger.info("send get resp {} {}", resp, resp.build().toByteArray().length);
        Buffer buf = Buffer.buffer(resp.build().toByteArray());
        socket.writeBinaryMessage(buf);
    }

    private void writeError(ServerWebSocket socket, ReplicatorError error) {
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
