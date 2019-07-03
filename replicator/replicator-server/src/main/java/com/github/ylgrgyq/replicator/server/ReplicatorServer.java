package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ReplicatorServer extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    @Override
    public void start(Future<Void> startFuture) {

        logger.info("jhajasdfasf");

        NetServerOptions options = new NetServerOptions();
        options.setHost("8888");
        NetServer server = vertx.createNetServer(options);

        SequenceGroups groups = new SequenceGroups();

        server.connectHandler(socket -> {
            socket.handler(buffer -> {
                String req = buffer.getString(0, buffer.length(), "UTF-8");
                ReplicatorCommand cmd;
                try {
                    cmd = ReplicatorCommand.parseFrom(buffer.getBytes());
                } catch (InvalidProtocolBufferException ex) {
                    writeError(socket, ReplicatorError.EUNKNOWNPROTOCOL);
                    return;
                }

                switch (cmd.getType()) {
                    case GET:
                        try {
                            String topic = cmd.getTopic();
                            Sequence seq = groups.getSequence(topic);
                            if (seq == null) {
                                seq = groups.createSequence(topic, new SequenceOptions());
                            }

                            long fromIndex = cmd.getFromIndex();
                            int limit = cmd.getLimit();
                            SyncLogEntries log = seq.syncLogs(fromIndex, limit);

                            ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
                            resp.setType(ReplicatorCommand.CommandType.GET_RESP);
                            resp.setLogs(log);
                            Buffer buf = Buffer.buffer(resp.build().toByteArray());
                            socket.write(buf, ret -> {
                                if (!ret.succeeded()) {
                                    logger.warn("write log failed", ret.cause());
                                }
                            });
                        } catch (ReplicatorException ex) {
                            writeError(socket, ex.getError());
                        }
                        break;
                    case SNAPSHOT:
                        try {
                            String topic = cmd.getTopic();
                            Sequence seq = groups.getSequence(topic);
                            if (seq == null) {
                                seq = groups.createSequence(topic, new SequenceOptions());
                            }
                            Snapshot snapshot = seq.getSnapshot();

                            ReplicatorCommand.Builder resp = ReplicatorCommand.newBuilder();
                            resp.setType(ReplicatorCommand.CommandType.SNAPSHOT_RESP);
                            resp.setSnapshot(snapshot);
                            Buffer buf = Buffer.buffer(resp.build().toByteArray());
                            socket.write(buf, ret -> {
                                if (!ret.succeeded()) {
                                    logger.warn("write snapshot failed");
                                }
                            });
                        } catch (ReplicatorException ex) {
                            writeError(socket, ex.getError());
                        }
                        break;
                }
            });
        });

        server.exceptionHandler(t -> logger.error("Receive unexpected error", t));

        server.listen(8888, ret -> {
            if (ret.succeeded()) {
                startFuture.complete();

                SequenceOptions op = new SequenceOptions();
                op.setSequenceExecutor(Executors.newSingleThreadExecutor());
                Sequence seq = groups.createSequence("hahaha", op);
                for (int i = 0; i < 100000; ++i) {
                    String msg = "wahaha-" + i;
                    seq.append(msg.getBytes(StandardCharsets.UTF_8));
                }
                logger.info("generate log done");
            } else {
                startFuture.fail(ret.cause());
            }
        });
    }

    private void writeError(NetSocket socket, ReplicatorError error){
        ReplicatorCommand.Builder builder = ReplicatorCommand.newBuilder();
        builder.setType(ReplicatorCommand.CommandType.ERROR);

        ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
        errorInfo.setErrorCode(error.getErrorCode());
        errorInfo.setErrorMsg(error.getMsg());
        builder.setError(errorInfo);
        Buffer buf = Buffer.buffer(builder.build().toByteArray());
        socket.write(buf, ret -> {
            if (!ret.succeeded()) {
                logger.warn("write log failed", ret.cause());
            }
        });
    }
}
