package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class VertxReplicatorServer extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(VertxReplicatorServer.class);

    @Override
    public void start(Future<Void> startFuture) {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost("localhost");

        HttpServer server = vertx.createHttpServer(options);

        SequenceGroups groups = new SequenceGroups();

        server.websocketHandler(socket -> {
                    VertxReplicateChannel channel = new VertxReplicateChannel(socket);
                    ReplicateRequestHandler handler = new Replica(channel);
                    socket.binaryMessageHandler(buffer -> {
                        ReplicatorCommand cmd;
                        try {
                            cmd = ReplicatorCommand.parseFrom(buffer.getBytes());
                        } catch (InvalidProtocolBufferException ex) {
                            channel.writeError(ReplicatorError.EUNKNOWNPROTOCOL);
                            return;
                        }

                        try {
                            switch (cmd.getType()) {
                                case HANDSHAKE:
                                    String topic = cmd.getTopic();
                                    handler.onStart(groups, topic);
                                    break;
                                case GET:
                                    long fromIndex = cmd.getFromIndex();
                                    int limit = cmd.getLimit();

                                    handler.heandleSyncLogs(fromIndex, limit);
                                    break;
                                case SNAPSHOT:
                                    handler.handleSyncSnapshot();
                                    break;
                            }
                        } catch (ReplicatorException ex) {
                            channel.writeError(ex.getError());
                        }
                    });
                }
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
}
