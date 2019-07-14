package com.github.ylgrgyq.replicator.server.connection.netty;

import com.github.ylgrgyq.replicator.server.AbstractReplicatorServer;
import com.github.ylgrgyq.replicator.server.ReplicatorOptions;
import com.github.ylgrgyq.replicator.server.SequenceGroups;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

import java.util.concurrent.CompletableFuture;

public class NettyReplicatorServer extends AbstractReplicatorServer {
    private ReplicatorOptions options;

    public NettyReplicatorServer(ReplicatorOptions options) {
        super();
        this.options = options;
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.group(new NioEventLoopGroup(1), new NioEventLoopGroup());
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel serverChannel) throws Exception {
                ChannelPipeline pipeline = serverChannel.pipeline();

                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(new WebSocketServerCompressionHandler());
                pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
                pipeline.addLast(new ReplicatorEncoder());
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
}
