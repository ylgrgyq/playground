package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.*;
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
    private StateMachineCaller stateMachineCaller;
    private long lastIndex;

    public NettyReplicatorClient(String topic, StateMachine stateMachine, ReplicatorClientOptions options) {
        super();
        this.topic = topic;
        this.options = options;
        this.group = new NioEventLoopGroup();
        this.stop = false;
        this.lastIndex = Long.MIN_VALUE;
        this.stateMachineCaller = new StateMachineCaller(stateMachine);
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

        ReplicatorClientHandler clientHandler = new ReplicatorClientHandler(topic, stateMachineCaller, lastIndex, options);
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
                pipeline.addLast(clientHandler);
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        logger.info("start connecting to {}:{}...", options.getHost(), options.getPort());
        bootstrap.connect(options.getHost(), options.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                channel.closeFuture().addListener(closeFuture -> {
                    logger.info("connection with {}:{} broken.", options.getHost(), options.getPort());
                    long nextIndex = clientHandler.getLastIndex();
                    if (nextIndex > lastIndex) {
                        lastIndex = nextIndex;
                    }
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
                        reconnectFuture = connect();
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

    public CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> terminateFuture = new CompletableFuture<>();

        stop = true;
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
        group.terminationFuture().addListener(f -> {
            if (f.isSuccess()) {
                stateMachineCaller.shutdown()
                        .whenComplete((ret, ex) -> {
                           if (ex != null) {
                               terminateFuture.completeExceptionally(ex);
                           } else {
                               terminateFuture.complete(null);
                           }
                        });
            } else {
                terminateFuture.completeExceptionally(f.cause());
            }
        });

        return terminateFuture;
    }
}
