package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.ReplicatorException;
import com.github.ylgrgyq.replicator.client.StateMachineCaller;
import com.github.ylgrgyq.replicator.proto.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReplicatorClientHandler extends SimpleChannelInboundHandler<ReplicatorCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private String topic;
    private long lastIndex;
    private StateMachineCaller stateMachineCaller;
    private volatile boolean suspend;
    private int pendingApplyLogsRequestCount;
    private ReplicatorClientOptions options;

    public ReplicatorClientHandler(String topic, StateMachineCaller stateMachineCaller, ReplicatorClientOptions options) {
        this.topic = topic;
        this.stateMachineCaller = stateMachineCaller;
        this.lastIndex = Long.MIN_VALUE;
        this.suspend = false;
        this.options = options;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ReplicatorCommand cmd) throws Exception {
        switch (cmd.getType()) {
            case HANDSHAKE_RESP:
                requestSnapshot(ctx.channel(), topic);
                break;
            case GET_RESP:
                SyncLogEntries logs = cmd.getLogs();
                handleSyncLogs(ctx, logs);
                break;
            case SNAPSHOT_RESP:
                Snapshot snapshot = cmd.getSnapshot();
                handleApplySnapshot(ctx, snapshot);
                break;
            case ERROR:
                ErrorInfo errorInfo = cmd.getError();
                if (errorInfo.getErrorCode() == 10001) {
                    requestSnapshot(ctx.channel(), topic);
                } else {
                    logger.error("got error", errorInfo);
                }
                break;
        }
    }

    private void requestSnapshot(Channel ch, String topic) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.SNAPSHOT);
        get.setTopic(topic);

        ch.writeAndFlush(get.build());
    }

    private void handleSyncLogs(ChannelHandlerContext ctx, SyncLogEntries logs) {
        List<LogEntry> entryList = logs.getEntriesList();

        if (!entryList.isEmpty()) {
            LogEntry firstEntry = entryList.get(0);
            LogEntry lastEntry = entryList.get(entryList.size() - 1);
            if (firstEntry.getIndex() > lastIndex + 1) {
                logger.warn("lastIndex:{} is too far behind sync logs {}", lastIndex, firstEntry.getIndex());
                requestSnapshot(ctx.channel(), topic);
            } else if (lastEntry.getIndex() > lastIndex) {
                int i = 0;
                for (; i < entryList.size(); ++i) {
                    LogEntry entry = entryList.get(i);
                    if (entry.getIndex() == lastIndex + 1) {
                        break;
                    }
                }

                lastIndex = lastEntry.getIndex();
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
                            requestLogs(ctx.channel(), topic, lastIndex);
                        });
                    }
                });
    }

    private void requestLogs(Channel ch, String topic, long fromIndex) {
        if (suspend) {
            return;
        }

        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.GET);
        get.setTopic(topic);
        get.setFromIndex(fromIndex);
        get.setLimit(100);

        ch.writeAndFlush(get.build());
    }

    private void handleApplySnapshot(ChannelHandlerContext ctx, Snapshot snapshot) {
        long snapshotId = snapshot.getId();
        if (snapshotId > lastIndex) {
            stateMachineCaller.applySnapshot(snapshot)
                    .whenComplete((ret, t) -> {
                        if (t != null) {
                            assert (t instanceof ReplicatorException) : t;
                            logger.warn("state machine is busy, apply snapshot latter", t);
                            suspend = true;
                            ctx.executor().schedule(() ->
                                    handleApplySnapshot(ctx, snapshot), 10, TimeUnit.SECONDS);
                        } else {
                            ctx.executor().submit(() -> {
                                assert lastIndex > snapshotId;
                                if (pendingApplyLogsRequestCount < options.getPendingFlushLogsLowWaterMark()) {
                                    suspend = false;
                                }
                                lastIndex = snapshotId;
                                requestLogs(ctx.channel(), topic, snapshotId);
                            });
                        }
                    });
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
            requestLogs(ctx.channel(), topic, lastIndex);
        } else if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            handshake(ctx.channel(), topic);
        }
    }

    public long getLastIndex() {
        return lastIndex;
    }

    public void handshake(Channel ch, String topic) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.HANDSHAKE);
        get.setTopic(topic);

        ch.writeAndFlush(get.build());
    }
}
