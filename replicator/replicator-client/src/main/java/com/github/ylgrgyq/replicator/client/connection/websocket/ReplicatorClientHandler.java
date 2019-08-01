package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.ReplicatorException;
import com.github.ylgrgyq.replicator.client.SnapshotManager;
import com.github.ylgrgyq.replicator.client.StateMachineCaller;
import com.github.ylgrgyq.replicator.common.CommandFactory;
import com.github.ylgrgyq.replicator.common.MessageType;
import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.common.RequestCommand;
import com.github.ylgrgyq.replicator.proto.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReplicatorClientHandler extends SimpleChannelInboundHandler<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private final String topic;
    private final StateMachineCaller stateMachineCaller;
    private final ReplicatorClientOptions options;
    private final SnapshotManager snapshotManager;

    private long lastId;
    private volatile boolean suspend;
    private int pendingApplyLogsRequestCount;

    public ReplicatorClientHandler(String topic, SnapshotManager snapshotManager,
                                   StateMachineCaller stateMachineCaller, ReplicatorClientOptions options) {
        this.topic = topic;
        this.snapshotManager = snapshotManager;
        this.stateMachineCaller = stateMachineCaller;
        this.lastId = Long.MIN_VALUE;
        this.suspend = false;
        this.options = options;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand cmd) {
        switch (cmd.getMessageType()) {
            case HANDSHAKE:
                handleHandshakeResp(ctx);
                break;
            case FETCH_LOGS:
                handleFetchLogs(ctx, cmd);
                break;
            case FETCH_SNAPSHOT:
                FetchSnapshotResponse response = cmd.getBody();
                Snapshot snapshot = response.getSnapshot();
                handleApplySnapshot(ctx, snapshot);
                break;
            case ERROR:
                ErrorInfo errorInfo = cmd.getBody();
                if (errorInfo.getErrorCode() == 10001) {
                    requestSnapshot(ctx.channel());
                } else {
                    logger.error("got error", errorInfo);
                }
                break;
        }
    }

    private void handleHandshakeResp(ChannelHandlerContext ctx) {
        Snapshot lastSnapshot = snapshotManager.getLastSnapshot();
        if (lastSnapshot != null && lastSnapshot.getId() > lastId) {
            handleApplySnapshot(ctx, lastSnapshot);
        } else {
            requestSnapshot(ctx.channel());
        }
    }

    private void requestSnapshot(Channel ch) {
        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.FETCH_SNAPSHOT);


        ch.writeAndFlush(req);
    }

    private void handleFetchLogs(ChannelHandlerContext ctx, RemotingCommand cmd) {
        FetchLogsResponse req = cmd.getBody();
        BatchLogEntries logs = req.getLogs();

        List<LogEntry> entryList = logs.getEntriesList();
        logger.info("handle fetch logs {}", entryList);
        if (!entryList.isEmpty()) {
            LogEntry firstEntry = entryList.get(0);
            LogEntry lastEntry = entryList.get(entryList.size() - 1);
            if (firstEntry.getId() > lastId + 1) {
                logger.warn("lastId:{} is too far behind sync logs {}", lastId, firstEntry.getId());
                requestSnapshot(ctx.channel());
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
                        .map(e -> e.getData().toByteArray())
                        .collect(Collectors.toList());

                handleApplyLogs(ctx, logDataList);
            }
        }
    }

    private void handleApplyLogs(ChannelHandlerContext ctx, List<byte[]> logs) {
        ++pendingApplyLogsRequestCount;
        stateMachineCaller.applyLogs(logs)
                .whenComplete((ret, t) -> {
                    if (t != null) {
                        assert (t instanceof ReplicatorException) : t;
                        logger.warn("state machine is busy, apply logs latter", t);
                        suspend = true;
                        ctx.executor().schedule(() -> handleApplyLogs(ctx, logs), 10, TimeUnit.SECONDS);
                    } else {
                        ctx.executor().submit(() -> {
                            if (--pendingApplyLogsRequestCount < options.getPendingFlushLogsLowWaterMark()) {
                                suspend = false;
                            }
                            assert pendingApplyLogsRequestCount >= 0 : pendingApplyLogsRequestCount;
                            requestLogs(ctx.channel(), topic, lastId);
                        });
                    }
                });
    }

    private void requestLogs(Channel ch, String topic, long fromId) {
        if (suspend) {
            return;
        }

        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.FETCH_LOGS);

        FetchLogsRequest fetchLogs = FetchLogsRequest.newBuilder()
                .setFromId(fromId)
                .setLimit(100)
                .build();

        req.setRequestObject(fetchLogs);

        ch.writeAndFlush(req);
    }

    private void handleApplySnapshot(ChannelHandlerContext ctx, Snapshot snapshot) {
        try {
            long snapshotId = snapshot.getId();
            if (snapshotId > lastId) {
                snapshotManager.storeSnapshot(snapshot);
                stateMachineCaller.applySnapshot(snapshot)
                        .whenComplete((ret, t) -> {
                            if (t != null) {
                                assert (t instanceof ReplicatorException) : t;
                                logger.warn("state machine is busy, apply snapshot latter", t);
                                suspend = true;
                                ctx.executor().schedule(() ->
                                                handleApplySnapshot(ctx, snapshot)
                                        , 10, TimeUnit.SECONDS);
                            } else {
                                ctx.executor().submit(() -> {
                                    assert lastId > snapshotId;
                                    if (pendingApplyLogsRequestCount < options.getPendingFlushLogsLowWaterMark()) {
                                        suspend = false;
                                    }
                                    lastId = snapshotId;
                                    requestLogs(ctx.channel(), topic, snapshotId);
                                });
                            }
                        });
            }
        } catch (IOException ex) {
            logger.error("Apply snapshot with id: {} failed", snapshot.getId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReplicatorException) {
            logger.error("replicator error", ((ReplicatorException) cause).getError());
        } else {
            logger.error("Got unexpected exception", cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            requestLogs(ctx.channel(), topic, lastId);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshake(ctx.channel(), topic);
    }

    public void handshake(Channel ch, String topic) {
        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.HANDSHAKE);
        HandshakeRequest request = HandshakeRequest.newBuilder()
                .setTopic(topic)
                .build();

        req.setRequestObject(request);

        ch.writeAndFlush(req);
    }
}
