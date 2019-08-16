package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.common.commands.*;
import com.github.ylgrgyq.replicator.common.protocol.v1.ReplicatorDecoder;
import com.github.ylgrgyq.replicator.common.protocol.v1.ReplicatorEncoder;
import com.github.ylgrgyq.replicator.server.sequence.*;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageFactory;
import com.github.ylgrgyq.replicator.server.storage.StorageHandle;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.ylgrgyq.replicator.common.Preconditions.checkState;

public final class ReplicatorServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    private final SequenceGroups groups;
    private final ReplicatorServerOptions options;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Storage<? extends StorageHandle> storage;
    private final CommandProcessor<ReplicatorRemotingContext> processor;
    private final ConnectionEventHandler connectionEventHandler;
    private final ServerStartStopSupport startStop;
    private volatile Channel serverChannel;


    public ReplicatorServer(ReplicatorServerOptions options) throws InterruptedException {
        this.groups = new SequenceGroups(options);
        this.options = options;
        this.bossGroup = options.getBossEventLoopGroup();
        this.workerGroup = options.getWorkerEventLoopGroup();
        this.storage = StorageFactory.createStorage(options.getStorageOptions());
        this.processor = new CommandProcessor<>();
        this.connectionEventHandler = new ConnectionEventHandler();
        this.startStop = new ServerStartStopSupport(GlobalEventExecutor.INSTANCE);

        registerProcessors();
    }

    private void registerProcessors() {
        processor.registerRequestProcessor(MessageType.HANDSHAKE, new HandshakeRequestProcessor());
        processor.registerRequestProcessor(MessageType.FETCH_LOGS, new FetchLogsRequestProcessor());
        processor.registerRequestProcessor(MessageType.FETCH_SNAPSHOT, new FetchSnapshotRequestProcessor());
    }

    public void onReceiveRemotingCommand(ReplicateChannel channel, Replica replica, RemotingCommand cmd) {
        ReplicatorRemotingContext ctx = new ReplicatorRemotingContext(channel, cmd, replica);
        processor.process(ctx, cmd);
    }

    public void addListener(ServerListener listener) {
        startStop.addListener(Objects.requireNonNull(listener, "listener"));
    }

    public boolean removeListener(ServerListener listener) {
        return startStop.removeListener(Objects.requireNonNull(listener, "listener"));
    }

    public CompletableFuture<Void> start() {
        return startStop.start(true);
    }

    public CompletableFuture<Void> stop() {
        return startStop.stop();
    }

    public String hostname() {
        return options.getHostname();
    }

    public SequenceAppender createSequence(String topic, SequenceOptions options) {
        checkState(startStop.isStarted(), "server is not started.");

        return groups.getOrCreateSequence(topic, storage, options);
    }

    public SequenceReader getSequenceReader(String topic) {
        checkState(startStop.isStarted(), "server is not started.");

        return groups.getSequence(topic);
    }

    public void dropSequence(String topic) {
        checkState(startStop.isStarted(), "server is not started.");

        groups.dropSequence(topic);
    }

    @Override
    public void close() {
        startStop.close();
    }

    private final class ServerStartStopSupport extends StartStopSupport<Void, Void, Void, ServerListener> {

        public ServerStartStopSupport(Executor executor) {
            super(executor);
        }

        @Override
        protected CompletionStage<Void> doStart(Void arg) throws Exception {
            CompletableFuture<Void> f = new CompletableFuture<>();
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

                    pipeline.addLast(new ReplicatorServerHandler(ReplicatorServer.this));
                }
            });

            bootstrap.bind(options.getPort())
                    .addListener((ChannelFuture future) -> {
                        final Channel ch = future.channel();
                        assert ch.eventLoop().inEventLoop();

                        if (future.isSuccess()) {
                            serverChannel = ch;
                            if (logger.isInfoEnabled()) {
                                final InetSocketAddress localAddr = (InetSocketAddress) ch.localAddress();
                                logger.info("Replicator service at {}", localAddr.getAddress().getHostAddress());
                            }
                            f.complete(null);
                        } else {
                            f.completeExceptionally(future.cause());
                        }
                    });

            return f;
        }

        @Override
        protected CompletionStage<Void> doStop(Void arg) throws Exception {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            // 1. close server channel
            final Future<?> serverChannelClosedFuture;
            if (serverChannel != null) {
                serverChannelClosedFuture = serverChannel.close();
            } else {
                serverChannelClosedFuture = ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
            }

            // 2. close child channels
            final CompletableFuture<Void> childChannelsClosedFuture = new CompletableFuture<>();
            serverChannelClosedFuture.addListener(unused -> {
                List<Channel> channels = new ArrayList<>(connectionEventHandler.children());
                if (channels.isEmpty()) {
                    childChannelsClosedFuture.complete(null);
                } else {
                    AtomicInteger channelsToClose = new AtomicInteger(channels.size());
                    ChannelFutureListener closeFuture = unused2 -> {
                        if (channelsToClose.decrementAndGet() == 0) {
                            childChannelsClosedFuture.complete(null);
                        }
                    };

                    for (Channel ch : channels) {
                        ch.close().addListener(closeFuture);
                    }
                }
            });

            // 3. shutdown worker and boss groups
            childChannelsClosedFuture.whenComplete((unused, unused2) -> {
                final Future<?> workerGroupStoppedFuture;
                if (options.shouldShutdownWorkerEventLoopGroup()) {
                    workerGroupStoppedFuture = workerGroup.shutdownGracefully();
                } else {
                    workerGroupStoppedFuture = ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                }

                workerGroupStoppedFuture.addListener(unused3 ->
                        bossGroup.shutdownGracefully()
                                .addListener(unused4 ->
                                        // 4. use thread in boss group to shutdown remaining stuffs
                                        finalDoStop(future))
                );
            });

            return future;
        }

        private void finalDoStop(CompletableFuture<Void> future) throws Exception {
            groups.shutdownAllSequences();

            if (options.shouldShutdownWorkerScheduledExecutor()) {
                ExecutorService executor = options.getWorkerScheduledExecutor();
                try {
                    executor.shutdown();
                    while (!executor.isTerminated()) {
                        try {
                            executor.awaitTermination(1, TimeUnit.DAYS);
                        } catch (InterruptedException ignore) {
                            // Do nothing.
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to shutdown the worker scheduled executor: {}", executor, e);
                }
            }

            storage.shutdown();

            future.complete(null);
        }

        @Override
        protected void notifyStarting(ServerListener listener, Void arg) throws Exception {
            listener.serverStarting(ReplicatorServer.this);
        }

        @Override
        protected void notifyStarted(ServerListener listener, Void arg, Void result) throws Exception {
            listener.serverStarted(ReplicatorServer.this);
        }

        @Override
        protected void notifyStopping(ServerListener listener, Void arg) throws Exception {
            listener.serverStopping(ReplicatorServer.this);
        }

        @Override
        protected void notifyStopped(ServerListener listener, Void arg) throws Exception {
            listener.serverStopped(ReplicatorServer.this);
        }

        @Override
        protected void notificationFailed(ServerListener listener, Throwable throwable) {
            logger.warn("Failed to notify server listener: {}", listener, throwable);
        }
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

    private class FetchLogsRequestProcessor implements Processor<ReplicatorRemotingContext, FetchLogsRequestCommand> {
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
