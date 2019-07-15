package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorException;
import com.github.ylgrgyq.replicator.client.StateMachine;
import com.github.ylgrgyq.replicator.proto.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ReplicatorClientHandler extends SimpleChannelInboundHandler<ReplicatorCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private String topic;
    private long lastIndex;
    private StateMachine stateMachine;

    public ReplicatorClientHandler(String topic, StateMachine stateMachine) {
        this.topic = topic;
        this.stateMachine = stateMachine;
        this.lastIndex = Long.MIN_VALUE;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ReplicatorCommand cmd) throws Exception {
        Channel ch = ctx.channel();

        switch (cmd.getType()) {
            case HANDSHAKE_RESP:
                requestSnapshot(ch, topic);
                break;
            case GET_RESP:
                SyncLogEntries logs = cmd.getLogs();
                logger.info("GET RESP {}", logs);
                List<LogEntry> entryList = logs.getEntriesList();

                if (entryList.isEmpty()) {
                    logger.info("GET RESP return empty");
                } else {
                    LogEntry firstEntry = entryList.get(0);
                    LogEntry lastEntry = entryList.get(entryList.size() - 1);
                    if (firstEntry.getIndex() > lastIndex + 1) {
                        requestSnapshot(ch, topic);
                    } else if (lastEntry.getIndex() > lastIndex) {
                        List<byte[]> entris = new ArrayList<>(entryList.size());
                        for (LogEntry entry : entryList) {
                            if (entry.getIndex() == lastIndex + 1) {
                                entris.add(entry.getData().toByteArray());
                                lastIndex = entry.getIndex();
                            }
                        }
                        stateMachine.apply(entris);
                        requestLogs(ch, topic, lastIndex);
                    }
                }
                break;
            case SNAPSHOT_RESP:
                Snapshot snapshot = cmd.getSnapshot();
                long snapshotId = snapshot.getId();
                if (snapshotId > lastIndex) {
                    stateMachine.snapshot(snapshot.toByteArray());
                    lastIndex = snapshotId;
                    requestLogs(ch, topic, snapshotId);
                }
                break;
            case ERROR:
                ErrorInfo errorInfo = cmd.getError();
                logger.info("got error {}", errorInfo);
                if (errorInfo.getErrorCode() == 10001) {
                    requestSnapshot(ch, topic);
                } else {
                    logger.error("got error", errorInfo);
                }
                break;
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

    public void handshake(Channel ch, String topic) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.HANDSHAKE);
        get.setTopic(topic);

        ch.writeAndFlush(get.build());
    }

    private void requestLogs(Channel ch, String topic, long fromIndex) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.GET);
        get.setTopic(topic);
        get.setFromIndex(fromIndex);
        get.setLimit(100);

        ch.writeAndFlush(get.build());
    }

    private void requestSnapshot(Channel ch, String topic) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.SNAPSHOT);
        get.setTopic(topic);

        ch.writeAndFlush(get.build());
    }
}
