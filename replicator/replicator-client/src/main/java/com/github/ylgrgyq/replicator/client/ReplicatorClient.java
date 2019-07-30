package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.client.connection.websocket.ReplicatorClientHandler;
import com.github.ylgrgyq.replicator.client.connection.websocket.ReplicatorDecoder;
import com.github.ylgrgyq.replicator.client.connection.websocket.ReplicatorEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClient.class);

    private ReplicatorClientOptions options;
    private EventLoopGroup group;
    private volatile boolean stop;
    private Channel channel;
    private String topic;
    private StateMachineCaller stateMachineCaller;
    private CompletableFuture<Void> terminationFuture;
    private SnapshotManager snapshotManager;

    public ReplicatorClient(String topic, StateMachine stateMachine, ReplicatorClientOptions options) throws IOException {
        super();
        this.topic = topic;
        this.options = options;
        this.group = new NioEventLoopGroup();
        this.stop = false;
        this.stateMachineCaller = new StateMachineCaller(stateMachine, this);
        this.snapshotManager = new SnapshotManager(options);

        this.snapshotManager.loadLastSnapshot();
    }

    public CompletableFuture<Void> start() {
        if (stop) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new ReplicatorException(ReplicatorError.ECLIENT_ALREADY_SHUTDOWN));
            return f;
        }

        stateMachineCaller.start();

        return connect();
    }

    private CompletableFuture<Void> connect(){
        CompletableFuture<Void> future = new CompletableFuture<>();

        URI uri = options.getUri();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addLast(new IdleStateHandler(options.getPingIntervalSec(), 0, 0));
                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                pipeline.addLast(new WebSocketClientProtocolHandler(uri, WebSocketVersion.V13, null, true,
                        new DefaultHttpHeaders(), 65536));
                pipeline.addLast(ReplicatorEncoder.INSTANCE);
                pipeline.addLast(ReplicatorDecoder.INSTANCE);
                pipeline.addLast(new ReplicatorClientHandler(topic, snapshotManager, stateMachineCaller, options));
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectionTimeoutMillis());
        logger.info("start connecting to {}:{}...", options.getHost(), options.getPort());
        bootstrap.connect(options.getHost(), options.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                channel.closeFuture().addListener(closeFuture -> {
                    logger.info("connection with {}:{} broken.", options.getHost(), options.getPort());

                    scheduleReconnect(channel.eventLoop());
                });
                logger.info("connection with {}:{} succeed.", options.getHost(), options.getPort());
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    private void scheduleReconnect(EventLoop loop) {
        if (!stop) {
            logger.info("reconnect to {}:{} in {} seconds.", options.getHost(),
                    options.getPort(), options.getReconnectDelaySeconds());
            loop.schedule(() -> {
                if (!stop) {
                    CompletableFuture<Void> reconnectFuture;
                    try {
                        CompletableFuture<Void> resetFuture = stateMachineCaller.resetStateMachine();
                        if (resetFuture.isCompletedExceptionally()) {
                            reconnectFuture = resetFuture;
                        } else {
                            reconnectFuture = connect();
                        }
                    } catch (Exception ex) {
                        reconnectFuture = new CompletableFuture<>();
                        reconnectFuture.completeExceptionally(ex);
                    }

                    reconnectFuture.whenComplete((ret, t) -> {
                        if (t != null) {
                            logger.error("reconnect to {}:{} failed.", options.getHost(), options.getPort(), t);
                            scheduleReconnect(loop);
                        }
                    });
                }
            }, options.getReconnectDelaySeconds(), TimeUnit.SECONDS);
        }
    }

    public synchronized CompletableFuture<Void> shutdown() {
        if (stop) {
            return terminationFuture;
        }

        stop = true;
        terminationFuture = new CompletableFuture<>();

        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
        group.terminationFuture().addListener(f -> {
            if (f.isSuccess()) {
                stateMachineCaller.shutdown()
                        .whenComplete((ret, ex) -> {
                           if (ex != null) {
                               terminationFuture.completeExceptionally(ex);
                           } else {
                               terminationFuture.complete(null);
                           }
                        });
            } else {
                terminationFuture.completeExceptionally(f.cause());
            }
        });

        return terminationFuture;
    }
}
