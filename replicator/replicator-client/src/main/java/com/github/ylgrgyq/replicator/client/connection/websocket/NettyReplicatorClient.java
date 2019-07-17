package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.StateMachine;
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

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NettyReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicatorClient.class);

    private ReplicatorClientOptions options;
    private EventLoopGroup group;
    private volatile boolean stop;
    private Channel channel;
    private String topic;
    private StateMachine stateMachine;
    private long lastIndex;

    public NettyReplicatorClient(String topic, StateMachine stateMachine, ReplicatorClientOptions options) {
        super();
        this.topic = topic;
        this.stateMachine = stateMachine;
        this.options = options;
        this.group = new NioEventLoopGroup();
        this.stop = false;
        this.lastIndex = Long.MIN_VALUE;
    }

    public CompletableFuture<Void> start() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();

        URI uri = new URI("ws://127.0.0.1:8888");
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);

        ReplicatorClientHandler clientHandler = new ReplicatorClientHandler(topic, stateMachine, lastIndex);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addLast(new IdleStateHandler(10, 0, 0));
                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                pipeline.addLast(new WebSocketClientProtocolHandler(uri, WebSocketVersion.V13, null, true,
                        new DefaultHttpHeaders(), 65536));
                pipeline.addLast(ReplicatorEncoder.INSTANCE);
                pipeline.addLast(ReplicatorDecoder.INSTANCE);
                pipeline.addLast(clientHandler);
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        bootstrap.connect(options.getHost(), options.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                channel.closeFuture().addListener(closeFuture -> {
                    logger.info("connection broken");
                    long nextIndex = clientHandler.getLastIndex();
                    if (nextIndex > lastIndex) {
                        lastIndex = nextIndex;
                    }
                    restart(channel.eventLoop());
                });
                logger.info("connect succeed");
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    private void restart(EventLoop loop) {
        if (!stop) {
            logger.info("reconnect in {} seconds", options.getReconnectDelaySeconds());
            loop.schedule(() -> {
                if (!stop) {
                    CompletableFuture<Void> restartF;
                    try {
                        logger.info("start reconnecting...");
                        restartF = start();
                    } catch (Exception ex) {
                        restartF = new CompletableFuture<>();
                        restartF.completeExceptionally(ex);
                    }

                    restartF.whenComplete((ret, t) -> {
                        if (t != null) {
                            logger.error("reconnect failed", t);
                            restart(loop);
                        }
                    });
                }
            }, options.getReconnectDelaySeconds(), TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        stop = true;
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }
}
