package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.CommandProcessor;
import com.github.ylgrgyq.replicator.common.Processor;
import com.github.ylgrgyq.replicator.common.ReplicateChannel;
import com.github.ylgrgyq.replicator.common.ReplicatorError;
import com.github.ylgrgyq.replicator.common.commands.*;
import com.github.ylgrgyq.replicator.common.protocol.v1.ReplicatorDecoder;
import com.github.ylgrgyq.replicator.common.protocol.v1.ReplicatorEncoder;
import com.github.ylgrgyq.replicator.server.sequence.*;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageFactory;
import com.github.ylgrgyq.replicator.server.storage.StorageHandle;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
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

    private final SequenceGroups groups;
    private final ReplicatorServerOptions options;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Storage<? extends StorageHandle> storage;
    private final CommandProcessor<ReplicatorRemotingContext> processor;
    private final ConnectionEventHandler connectionEventHandler;
    private volatile boolean stop;

    public ReplicatorServerImpl(ReplicatorServerOptions options) throws InterruptedException {
        this.groups = new SequenceGroups(options);
        this.options = options;
        this.bossGroup = options.getBossEventLoopGroup();
        this.workerGroup = options.getWorkerEventLoopGroup();
        this.storage = StorageFactory.createStorage(options.getStorageOptions());
        this.processor = new CommandProcessor<>();
        this.connectionEventHandler = new ConnectionEventHandler();

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
        bootstrap.handler(connectionEventHandler);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel serverChannel) {
                ChannelPipeline pipeline = serverChannel.pipeline();

                pipeline.addLast(new ReplicatorEncoder());
                pipeline.addLast(new ReplicatorDecoder());
                pipeline.addLast(new IdleStateHandler(options.getConnectionReadTimeoutSecs(), 0, 0));

                pipeline.addLast(new ReplicatorServerHandler(ReplicatorServerImpl.this));
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

        for (Channel ch : connectionEventHandler.children()) {
            ch.close();
        }

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

    private class HandshakeRequestProcessor implements Processor<ReplicatorRemotingContext, HandshakeRequestCommand> {
        @Override
        public void process(ReplicatorRemotingContext ctx, HandshakeRequestCommand handshake) {
            String topic = handshake.getTopic();

            SequenceImpl seq = groups.getSequence(topic);
            if (seq == null) {
                ctx.sendError(ReplicatorError.ETOPIC_NOT_FOUND);
            }

            ctx.getReplica().onStart(ctx, seq, handshake);
        }
    }

    private class FetchLogsRequestProcessor implements  Processor<ReplicatorRemotingContext, FetchLogsRequestCommand> {
        @Override
        public void process(ReplicatorRemotingContext ctx, FetchLogsRequestCommand cmd) {
            ctx.getReplica().handleFetchLogs(ctx, cmd);
        }
    }

    private class FetchSnapshotRequestProcessor implements Processor<ReplicatorRemotingContext, FetchSnapshotRequestCommand> {
        @Override
        public void process(ReplicatorRemotingContext ctx, FetchSnapshotRequestCommand cmd) {
            ctx.getReplica().handleFetchSnapshot(ctx, cmd);
        }
    }
}
