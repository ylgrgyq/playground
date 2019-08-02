package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.client.connection.websocket.ReplicatorClientHandler;
import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.proto.*;
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

    private final ReplicatorClientOptions options;
    private final EventLoopGroup group;
    private volatile boolean stop;
    private Channel channel;
    private String topic;
    private StateMachineCaller stateMachineCaller;
    private CompletableFuture<Void> terminationFuture;
    private SnapshotManager snapshotManager;
    private CommandProcessor processor;
    private volatile long lastId;
    private volatile boolean suspend;
    private int pendingApplyLogsRequestCount;
    private NettyReplicateChannel remotingChannel;
    private BlockingQueue<Task> taskQueue = new DelayQueue<>();
    private Thread worker;


    public ReplicatorClientImpl(String topic, StateMachine stateMachine, ReplicatorClientOptions options) throws IOException {
        super();
        this.topic = topic;
        this.options = options;
        this.group = new NioEventLoopGroup();
        this.stop = false;
        this.stateMachineCaller = new StateMachineCaller(stateMachine, this);
        this.snapshotManager = new SnapshotManager(options);

        this.suspend = false;
        this.processor = new CommandProcessor();
        this.worker = new Thread(new Worker());
        registerProcessors();
    }

    @Override
    public CompletableFuture<Void> start() {
        if (stop) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new ReplicatorException(ReplicatorError.ECLIENT_ALREADY_SHUTDOWN));
            return f;
        }

        worker.start();
        stateMachineCaller.start();

        return connect();
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
                channel = f.channel();
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
    public synchronized CompletableFuture<Void> shutdown() {
        if (stop) {
            return terminationFuture;
        }

        stop = true;
        terminationFuture = new CompletableFuture<>();

        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
        group.terminationFuture().addListener(f -> {
            if (f.isSuccess()) {
                stateMachineCaller.shutdown()
                        .whenComplete((ret, ex) -> {
                            if (ex != null) {
                                terminationFuture.completeExceptionally(ex);
                            } else {
                                terminationFuture.complete(null);
                            }
                        });
            } else {
                terminationFuture.completeExceptionally(f.cause());
            }
        });

        return terminationFuture;
    }

    @Override
    public void onChannelActive(NettyReplicateChannel channel) {
        this.remotingChannel = channel;

        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.HANDSHAKE);
        HandshakeRequest request = HandshakeRequest.newBuilder()
                .setTopic(topic)
                .build();

        req.setRequestObject(request);

        remotingChannel.writeRemoting(req);
    }

    @Override
    public void onRetryFetchLogs() {
        taskQueue.offer(new DelayedTask(this::requestLogs));
    }

    @Override
    public void onReceiveRemotingMsg(RemotingCommand cmd) {
        taskQueue.offer(new ProcessRemotingCommandTask(cmd));
    }

    private void registerProcessors() {
        processor.registerResponseProcessor(MessageType.HANDSHAKE, new HandshakeResponseProcessor());
        processor.registerResponseProcessor(MessageType.FETCH_LOGS, new FetchLogsResponseProcessor());
        processor.registerResponseProcessor(MessageType.FETCH_SNAPSHOT, new FetchSnapshotResponseProcessor());
        processor.registerResponseProcessor(MessageType.ERROR, cmd -> {
            ErrorInfo errorInfo = cmd.getBody();
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
            processor.process(cmd);
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

    private class HandshakeResponseProcessor implements Processor<ResponseCommand> {
        @Override
        public void process(ResponseCommand cmd) {
            Snapshot lastSnapshot = snapshotManager.getLastSnapshot();
            if (lastSnapshot != null && lastSnapshot.getId() > lastId) {
                handleApplySnapshot(lastSnapshot);
            } else {
                requestSnapshot();
            }
        }
    }

    private class FetchLogsResponseProcessor implements Processor<ResponseCommand> {
        @Override
        public void process(ResponseCommand cmd) {
            FetchLogsResponse req = cmd.getBody();
            BatchLogEntries logs = req.getLogs();

            List<com.github.ylgrgyq.replicator.proto.LogEntry> entryList = logs.getEntriesList();
            if (!entryList.isEmpty()) {
                com.github.ylgrgyq.replicator.proto.LogEntry firstEntry = entryList.get(0);
                com.github.ylgrgyq.replicator.proto.LogEntry lastEntry = entryList.get(entryList.size() - 1);
                if (firstEntry.getId() > lastId + 1) {
                    logger.info("lastId:{} is too far behind sync logs {}", lastId, firstEntry.getId());
                    requestSnapshot();
                } else if (lastEntry.getId() > lastId) {
                    int i = 0;
                    for (; i < entryList.size(); ++i) {
                        com.github.ylgrgyq.replicator.proto.LogEntry entry = entryList.get(i);
                        if (entry.getId() == lastId + 1) {
                            break;
                        }
                    }

                    lastId = lastEntry.getId();
                    List<byte[]> logDataList = entryList.subList(i, entryList.size())
                            .stream()
                            .map(e -> e.getData().toByteArray())
                            .collect(Collectors.toList());

                    handleApplyLogs(logDataList);
                }
            }
        }
    }

    private class FetchSnapshotResponseProcessor implements Processor<ResponseCommand> {
        @Override
        public void process(ResponseCommand cmd) {
            FetchSnapshotResponse response = cmd.getBody();
            Snapshot snapshot = response.getSnapshot();
            handleApplySnapshot(snapshot);
        }
    }

    private void requestSnapshot() {
        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.FETCH_SNAPSHOT);

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
        if (suspend) {
            return;
        }

        FetchLogsRequest fetchLogs = FetchLogsRequest.newBuilder()
                .setFromId(lastId)
                .setLimit(options.getFetchLogsBatchSize())
                .build();

        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.FETCH_LOGS);
        req.setRequestObject(fetchLogs);

        remotingChannel.writeRemoting(req);
    }
}
