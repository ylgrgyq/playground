package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.client.connection.tcp.ReplicatorClientHandler;
import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.common.protocol.v1.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ReplicatorClientImpl implements ReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientImpl.class);
    private static NamedThreadFactory workerFactory = new NamedThreadFactory("ReplicatorClientWorker");

    private final ReplicatorClientOptions options;
    private final EventLoopGroup group;
    private final String topic;
    private final StateMachineCaller stateMachineCaller;
    private final SnapshotManager snapshotManager;
    private final CommandProcessor<RemotingContext> processor;
    private final BlockingQueue<Task> taskQueue = new DelayQueue<>();
    private final Thread worker;

    private volatile boolean stop;
    private volatile long lastId;
    private volatile boolean suspend;
    private volatile ReplicateChannel remotingChannel;
    private int pendingApplyLogsRequestCount;

    public ReplicatorClientImpl(String topic, StateMachine stateMachine, ReplicatorClientOptions options)
            throws IOException, InterruptedException {
        this.topic = topic;
        this.options = options;
        this.group = new NioEventLoopGroup();
        this.stop = false;
        this.snapshotManager = new SnapshotManager(options);
        this.suspend = false;
        this.stateMachineCaller = new StateMachineCaller(stateMachine, this);
        this.processor = new CommandProcessor<>();
        this.worker = workerFactory.newThread(new Worker());
        registerProcessors();

        worker.start();

        CompletableFuture<Void> future = connect();
        // wait first connection success
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw new ReplicatorException(ex);
        }
    }

    private CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addLast(new ReplicatorEncoder());
                pipeline.addLast(new ReplicatorDecoder());
                pipeline.addLast(new IdleStateHandler(options.getPingIntervalSec(), 0, 0));
                pipeline.addLast(new ReplicatorClientHandler(ReplicatorClientImpl.this));
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectionTimeoutMillis());
        logger.info("start connecting to {}:{}...", options.getHost(), options.getPort());
        bootstrap.connect(options.getHost(), options.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                Channel channel = f.channel();
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

    @Override
    public void shutdown() throws Exception {
        if (stop) {
            return;
        }
        stop = true;
        taskQueue.offer(new WakeUpTask());
        worker.join();

        if (remotingChannel != null) {
            remotingChannel.close();
        }

        group.shutdownGracefully();
        stateMachineCaller.shutdown();
    }

    @Override
    public void onStart(ReplicateChannel channel) {
        if (remotingChannel != null) {
            remotingChannel.close();
        }

        remotingChannel = channel;

        HandshakeRequestCommand request = new HandshakeRequestCommand();
        request.setTopic(topic);

        remotingChannel.writeRemoting(request);
    }

    @Override
    public void onReceiveRemotingMsgTimeout() {
        if (!stop) {
            taskQueue.offer(new DelayedTask(this::requestLogs));
        }
    }

    @Override
    public void onReceiveRemotingMsg(RemotingCommand cmd) {
        if (!stop) {
            taskQueue.offer(new ProcessRemotingCommandTask(cmd));
        }
    }

    private void registerProcessors() {
        processor.registerResponseProcessor(MessageType.HANDSHAKE, new HandshakeResponseProcessor());
        processor.registerResponseProcessor(MessageType.FETCH_LOGS, new FetchLogsResponseProcessor());
        processor.registerResponseProcessor(MessageType.FETCH_SNAPSHOT, new FetchSnapshotResponseProcessor());
        processor.registerOnewayCommandProcessor(MessageType.ERROR, (Context ctx, ErrorCommand errorInfo) -> {
            if (errorInfo.getErrorCode() == 10001) {
                requestSnapshot();
            } else {
                logger.error("Got error from server: {}", errorInfo);
            }
        });
    }

    private abstract class Task implements Delayed, Runnable {
        long createNanos;

        Task() {
            this.createNanos = System.nanoTime();
        }
    }

    private class ProcessRemotingCommandTask extends Task {
        private RemotingCommand cmd;

        ProcessRemotingCommandTask(RemotingCommand cmd) {
            this.cmd = cmd;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            Task taskO = (Task) o;
            return (int) (createNanos - taskO.createNanos);
        }

        @Override
        public void run() {
            RemotingContext context = new RemotingContext(remotingChannel, cmd);
            processor.process(context, cmd);
        }
    }

    private class DelayedTask extends Task {
        private long delayedNanos;
        private Runnable job;

        DelayedTask(Runnable job) {
            this.delayedNanos = createNanos;
            this.job = job;
        }

        DelayedTask(long delayed, TimeUnit unit, Runnable job) {
            this.delayedNanos = unit.toNanos(delayed) + createNanos;
            this.job = job;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return delayedNanos - System.nanoTime();
        }

        @Override
        public int compareTo(Delayed o) {
            Task taskO = (Task) o;
            return (int) (delayedNanos - taskO.createNanos);
        }

        @Override
        public void run() {
            job.run();
        }
    }

    private class WakeUpTask extends Task {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return -1;
        }

        @Override
        public void run() {
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                try {
                    Task task = taskQueue.take();

                    task.run();
                } catch (InterruptedException ex) {
                    // continue
                } catch (Exception ex) {
                    logger.error("Replicator client got unexpected exception", ex);
                }
            }
        }
    }

    private class HandshakeResponseProcessor implements Processor<RemotingContext, FetchSnapshotResponseCommand> {
        @Override
        public void process(RemotingContext ctx, FetchSnapshotResponseCommand cmd) {
            Snapshot lastSnapshot = snapshotManager.getLastSnapshot();
            if (lastSnapshot != null && lastSnapshot.getId() > lastId) {
                handleApplySnapshot(lastSnapshot);
            } else {
                requestSnapshot();
            }
        }
    }

    private class FetchLogsResponseProcessor implements Processor<RemotingContext, FetchLogsResponseCommand> {
        @Override
        public void process(RemotingContext ctx, FetchLogsResponseCommand req) {
            List<LogEntry> entryList = req.getLogs();
            if (!entryList.isEmpty()) {
                LogEntry firstEntry = entryList.get(0);
                LogEntry lastEntry = entryList.get(entryList.size() - 1);
                if (firstEntry.getId() > lastId + 1) {
                    logger.info("lastId:{} is too far behind sync logs {}", lastId, firstEntry.getId());
                    requestSnapshot();
                } else if (lastEntry.getId() > lastId) {
                    int i = 0;
                    for (; i < entryList.size(); ++i) {
                        LogEntry entry = entryList.get(i);
                        if (entry.getId() == lastId + 1) {
                            break;
                        }
                    }

                    lastId = lastEntry.getId();
                    List<byte[]> logDataList = entryList.subList(i, entryList.size())
                            .stream()
                            .map(LogEntry::getData)
                            .collect(Collectors.toList());

                    handleApplyLogs(logDataList);
                }
            }
        }
    }

    private class FetchSnapshotResponseProcessor implements Processor<RemotingContext, FetchSnapshotResponseCommand> {
        @Override
        public void process(RemotingContext ctx, FetchSnapshotResponseCommand response) {
            Snapshot snapshot = response.getSnapshot();
            handleApplySnapshot(snapshot);
        }
    }

    private void requestSnapshot() {
        RequestCommand req = CommandFactoryManager.createRequest(MessageType.FETCH_SNAPSHOT);

        remotingChannel.writeRemoting(req);
    }

    private void handleApplySnapshot(Snapshot snapshot) {
        try {
            long snapshotId = snapshot.getId();
            if (snapshotId > lastId) {
                snapshotManager.storeSnapshot(snapshot);
                stateMachineCaller.applySnapshot(snapshot)
                        .whenComplete((ret, t) -> {
                            if (t != null) {
                                assert (t instanceof ReplicatorException) : t;
                                logger.warn("State machine is busy, apply snapshot latter", t);
                                suspend = true;
                                taskQueue.offer(new DelayedTask(10, TimeUnit.SECONDS,
                                        () -> handleApplySnapshot(snapshot)));
                            } else {
                                taskQueue.offer(new DelayedTask(
                                        () -> {
                                            assert lastId > snapshotId;
                                            if (pendingApplyLogsRequestCount < options.getPendingFlushLogsLowWaterMark()) {
                                                suspend = false;
                                            }
                                            lastId = snapshotId;
                                            requestLogs();
                                        }));
                            }
                        });
            } else {
                requestLogs();
            }
        } catch (IOException ex) {
            logger.error("Apply snapshot with id: {} failed", snapshot.getId());
        }
    }

    private void handleApplyLogs(List<byte[]> logs) {
        ++pendingApplyLogsRequestCount;
        stateMachineCaller.applyLogs(logs)
                .whenComplete((ret, t) -> {
                    if (t != null) {
                        assert (t instanceof ReplicatorException) : t;
                        logger.warn("State machine is busy, apply logs latter", t);
                        suspend = true;
                        taskQueue.offer(new DelayedTask(10, TimeUnit.SECONDS,
                                () -> handleApplyLogs(logs)));
                    } else {
                        taskQueue.offer(new DelayedTask(
                                () -> {
                                    if (--pendingApplyLogsRequestCount < options.getPendingFlushLogsLowWaterMark()) {
                                        suspend = false;
                                    }
                                    assert pendingApplyLogsRequestCount >= 0 : pendingApplyLogsRequestCount;
                                    requestLogs();
                                }));
                    }
                });
    }

    private void requestLogs() {
        if (suspend || stop) {
            return;
        }

        FetchLogsRequestCommand fetchLogs = new FetchLogsRequestCommand();
        fetchLogs.setFromId(lastId);
        fetchLogs.setLimit(options.getFetchLogsBatchSize());

        remotingChannel.writeRemoting(fetchLogs);
    }
}
