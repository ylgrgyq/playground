package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorOptions;
import com.github.ylgrgyq.replicator.client.StateMachine;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class NettyReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicatorClient.class);

    private ReplicatorOptions options;
    private EventLoopGroup parentGroup;
    private EventLoopGroup childGroup;
    private String topic;
    private StateMachine stateMachine;

    public NettyReplicatorClient(String topic, StateMachine stateMachine, ReplicatorOptions options) {
        super();
        this.options = options;
        this.parentGroup = new NioEventLoopGroup(1);
        this.childGroup = new NioEventLoopGroup();
        this.topic = topic;
        this.stateMachine = stateMachine;
    }

    public CompletableFuture<Void> start() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();


        WebSocketClientHandshaker handshaker =
                        WebSocketClientHandshakerFactory.newHandshaker(
                                new URI( "ws://127.0.0.1:8888"), WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        ReplicatorClientHandler handler = new ReplicatorClientHandler(topic, stateMachine, handshaker);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(childGroup);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();

//                pipeline.addLast(new IdleStateHandler(30, 0, 0));
                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                pipeline.addLast(new ReplicatorEncoder());
                pipeline.addLast(new ReplicatorDecoder());
                pipeline.addLast(handler);
            }
        });


        Channel ch = bootstrap.connect(options.getHost(), options.getPort()).sync().channel();
        handler.handshakeFuture().addListener(f2 -> {
            if (f2.isSuccess()){
                logger.warn("handshake success");
                handler.handshake(ch, topic);
                future.complete(null);
            } else {
                logger.warn("handshak failed", f2.cause());
                future.completeExceptionally(f2.cause());
            }
        });

        return future;
    }

    public void shutdown() {
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
    }
}
