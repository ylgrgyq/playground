package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class VertxReplicatorServer {
    private static final Logger logger = LoggerFactory.getLogger(VertxReplicatorServerBootstrap.class);

    private Vertx vertx;
    private ReplicatorOptions options;
    private SequenceGroups groups;

    public VertxReplicatorServer(Vertx vertx, ReplicatorOptions options){
        this.vertx = vertx;
        this.options = options;
        this.groups = new SequenceGroups();
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setHost("localhost");

        HttpServer server = vertx.createHttpServer(httpServerOptions);
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

        server.listen(options.getPort(), ret -> {
            if (ret.succeeded()) {
                future.complete(null);
            } else {
                future.completeExceptionally(ret.cause());
            }
        });

        return future;
    }

    public Appender createSequenceAppender(String topic) {
        SequenceOptions op = new SequenceOptions();
        op.setSequenceExecutor(Executors.newSingleThreadExecutor());
        Sequence seq = groups.createSequence(topic, op);
        return new Appender(seq);
    }

    public static class Appender{
        private Sequence seq;
        private Appender(Sequence seq) {
            this.seq = seq;
        }
        public void append(long id, byte[] log) {
            seq.append(id, log);
        }
    }
}
