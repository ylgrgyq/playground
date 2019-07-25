package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.connection.websocket.ReplicatorDecoder;
import com.github.ylgrgyq.replicator.server.connection.websocket.ReplicatorEncoder;
import com.github.ylgrgyq.replicator.server.connection.websocket.ReplicatorServerHandler;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerImpl implements ReplicatorServer{
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    private SequenceGroups groups;
    private ReplicatorServerOptions options;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Storage<?> storage;

    public ReplicatorServerImpl(ReplicatorServerOptions options) {
        this.groups = new SequenceGroups();
        this.options = options;
        this.bossGroup = options.getBossEventLoopGroup();
        this.workerGroup = options.getWorkerEventLoopGroup();
        this.storage = StorageFactory.createStorage(options);
    }

    @Override
    public boolean start() {
        if (!storage.init()) {
            logger.error("Init storage failed");
            return false;
        }

        if (!initServer()) {
            logger.error("Init server failed");
            return false;
        }

        return true;
    }

    private boolean initServer() {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.group(bossGroup, workerGroup);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel serverChannel) {
                    ChannelPipeline pipeline = serverChannel.pipeline();

                    pipeline.addLast(new IdleStateHandler(30, 0, 0));
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast(new HttpObjectAggregator(65536));
                    pipeline.addLast(new WebSocketServerCompressionHandler());
                    pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
                    pipeline.addLast(ReplicatorEncoder.INSTANCE);
                    pipeline.addLast(ReplicatorDecoder.INSTANCE);
                    pipeline.addLast(new ReplicatorServerHandler(groups));
                }
            });

            bootstrap.bind(options.getPort()).sync();
            return true;
        } catch (Exception ex) {
            logger.error("Init replicator server failed", ex);
        }

        return false;
    }

    @Override
    public SequenceAppender createSequence(String topic, SequenceOptions options) {
        Sequence seq = groups.getOrCreateSequence(topic, storage, options);
        return new SequenceAppender(seq);
    }

    @Override
    public boolean removeSequence(String topic) {
        return groups.deleteSequence(topic);
    }

    @Override
    public void shutdown() {
        if (options.isShouldShutdownBossEventLoopGroup()) {
            bossGroup.shutdownGracefully();
        }

        if (options.isShouldShutdownWorkerEventLoopGroup()) {
            workerGroup.shutdownGracefully();
        }

        groups.shutdown();

        storage.shutdown();
    }
}
