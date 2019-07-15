package com.github.ylgrgyq.replicator.server.connection.websocket;

import com.github.ylgrgyq.replicator.server.AbstractReplicatorServer;
import com.github.ylgrgyq.replicator.server.ReplicatorOptions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.CompletableFuture;

public class NettyReplicatorServer extends AbstractReplicatorServer {
    private ReplicatorOptions options;
    private EventLoopGroup parentGroup;
    private EventLoopGroup childGroup;

    public NettyReplicatorServer(ReplicatorOptions options) {
        super();
        this.options = options;
        this.parentGroup = new NioEventLoopGroup(1);
        this.childGroup = new NioEventLoopGroup();
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.group(parentGroup, childGroup);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel serverChannel) throws Exception {
                ChannelPipeline pipeline = serverChannel.pipeline();

                pipeline.addLast(new IdleStateHandler(30, 0, 0));
                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(new WebSocketServerCompressionHandler());
                pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
                pipeline.addLast(new ReplicatorEncoder());
                pipeline.addLast(new ReplicatorDecoder());
                pipeline.addLast(new ReplicatorServerHandler(getGroups()));
            }
        });

        bootstrap.bind(options.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()){
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    @Override
    public void shutdown() {
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
    }
}
