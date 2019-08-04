package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.proto.FetchLogsRequest;
import com.github.ylgrgyq.replicator.proto.FetchSnapshotRequest;
import com.github.ylgrgyq.replicator.proto.HandshakeRequest;
import com.github.ylgrgyq.replicator.server.connection.tcp.ConnectionManager;
import com.github.ylgrgyq.replicator.server.connection.tcp.ReplicatorServerHandler;
import com.github.ylgrgyq.replicator.server.sequence.*;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageFactory;
import com.github.ylgrgyq.replicator.server.storage.StorageHandle;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerImpl implements ReplicatorServer {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    private SequenceGroups groups;
    private ReplicatorServerOptions options;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Storage<? extends StorageHandle> storage;
    private CommandProcessor<ReplicatorRemotingContext> processor;
    private ConnectionManager connectionManager;
    private volatile boolean stop;

    public ReplicatorServerImpl(ReplicatorServerOptions options) throws InterruptedException {
        this.groups = new SequenceGroups(options);
        this.options = options;
        this.bossGroup = options.getBossEventLoopGroup();
        this.workerGroup = options.getWorkerEventLoopGroup();
        this.storage = StorageFactory.createStorage(options.getStorageOptions());
        this.processor = new CommandProcessor<>();
        this.connectionManager = new ConnectionManager();

        registerProcessors();
        initServer();
    }

    private void registerProcessors() {
        processor.registerRequestProcessor(MessageType.HANDSHAKE, new HandshakeRequestProcessor());
        processor.registerRequestProcessor(MessageType.FETCH_LOGS, new FetchLogsRequestProcessor());
        processor.registerRequestProcessor(MessageType.FETCH_SNAPSHOT, new FetchSnapshotRequestProcessor());
    }

    private void initServer() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.group(bossGroup, workerGroup);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel serverChannel) {
                ChannelPipeline pipeline = serverChannel.pipeline();

                pipeline.addLast(new ReplicatorEncoder());
                pipeline.addLast(new ReplicatorDecoder());
                pipeline.addLast(new IdleStateHandler(options.getConnectionReadTimeoutSecs(), 0, 0));

                pipeline.addLast(new ReplicatorServerHandler(ReplicatorServerImpl.this, connectionManager));
            }
        });

        bootstrap.bind(options.getHost(), options.getPort()).sync();
    }

    @Override
    public void onReceiveRemotingCommand(ReplicateChannel channel, Replica replica, RemotingCommand cmd) {
        if (stop) {
            return;
        }

        ReplicatorRemotingContext ctx = new ReplicatorRemotingContext(channel, cmd, replica);
        processor.process(ctx, cmd);
    }

    @Override
    public SequenceAppender createSequence(String topic, SequenceOptions options) {
        return groups.getOrCreateSequence(topic, storage, options);
    }

    @Override
    public SequenceReader getSequenceReader(String topic) {
        return groups.getSequence(topic);
    }

    @Override
    public void dropSequence(String topic) {
        groups.dropSequence(topic);
    }

    @Override
    public synchronized void shutdown() throws InterruptedException{
        if (stop) {
            return;
        }

        stop = true;

        connectionManager.shutdown();

        if (options.shouldShutdownBossEventLoopGroup()) {
            bossGroup.shutdownGracefully();
        }

        if (options.shouldShutdownWorkerEventLoopGroup()) {
            workerGroup.shutdownGracefully();
        }

        groups.shutdownAllSequences();

        if (options.shouldShutdownWorkerScheduledExecutor()) {
            options.getWorkerScheduledExecutor().shutdown();
        }

        storage.shutdown();
    }

    private class HandshakeRequestProcessor implements Processor<ReplicatorRemotingContext, HandshakeRequest> {
        @Override
        public void process(ReplicatorRemotingContext ctx, HandshakeRequest handshake) {
            String topic = handshake.getTopic();

            SequenceImpl seq = groups.getSequence(topic);
            if (seq == null) {
                ctx.sendError(ReplicatorError.ETOPIC_NOT_FOUND);
            }

            ctx.getReplica().onStart(ctx, seq);
        }
    }

    private class FetchLogsRequestProcessor implements  Processor<ReplicatorRemotingContext, FetchLogsRequest> {
        @Override
        public void process(ReplicatorRemotingContext ctx, FetchLogsRequest cmd) {
            ctx.getReplica().handleFetchLogs(ctx, cmd);
        }
    }

    private class FetchSnapshotRequestProcessor implements Processor<ReplicatorRemotingContext, FetchSnapshotRequest> {
        @Override
        public void process(ReplicatorRemotingContext ctx, FetchSnapshotRequest cmd) {
            ctx.getReplica().handleFetchSnapshot(ctx);
        }
    }
}
