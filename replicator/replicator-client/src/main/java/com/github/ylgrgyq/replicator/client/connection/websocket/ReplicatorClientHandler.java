package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorException;
import com.github.ylgrgyq.replicator.client.StateMachine;
import com.github.ylgrgyq.replicator.proto.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ReplicatorClientHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private String topic;
    private long lastIndex;
    private StateMachine stateMachine;

    public ReplicatorClientHandler(String topic, StateMachine stateMachine, WebSocketClientHandshaker handshaker) {
        this.topic = topic;
        this.stateMachine = stateMachine;
        this.handshaker = handshaker;
        this.lastIndex = -1;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);

        handshaker.handshake(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                System.out.println("WebSocket Client connected!");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                System.out.println("WebSocket Client failed to connect");
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        if (msg instanceof ReplicatorCommand) {
            ReplicatorCommand cmd = (ReplicatorCommand) msg;


            switch (cmd.getType()) {
                case HANDSHAKE_RESP:
                    requestSnapshot(ch, topic);
                    break;
                case GET_RESP:
                    SyncLogEntries logs = cmd.getLogs();
                    logger.info("GET RESP {}", logs);
                    List<LogEntry> entryList = logs.getEntriesList();
                    List<byte[]> entris = new ArrayList<>(entryList.size());
                    if (entryList.isEmpty()) {
                        logger.info("GET RESP return empty");
                    } else {
                        LogEntry firstEntry = entryList.get(0);
                        if (lastIndex + 1 == firstEntry.getIndex()) {
                            for (LogEntry entry : entryList) {
                                entris.add(entry.getData().toByteArray());
                                lastIndex = entry.getIndex();
                            }
                            stateMachine.apply(entris);
                            requestLogs(ch, topic, lastIndex);
                        } else {
                            requestSnapshot(ch, topic);
                        }
                    }
                    break;
                case SNAPSHOT_RESP:
                    Snapshot snapshot = cmd.getSnapshot();
                    stateMachine.snapshot(snapshot.toByteArray());
                    lastIndex = snapshot.getId();
                    requestLogs(ch, topic, snapshot.getId());
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
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReplicatorException) {
            logger.error("replicator error", ((ReplicatorException) cause).getError());
        } else {
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }

            logger.error("Got unexpected exception", cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
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
