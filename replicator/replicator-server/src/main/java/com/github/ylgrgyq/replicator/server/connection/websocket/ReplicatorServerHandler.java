package com.github.ylgrgyq.replicator.server.connection.websocket;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.server.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerHandler extends SimpleChannelInboundHandler<ReplicatorCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServerHandler.class);
    private SequenceGroups groups;
    private Replica replica;
    private NettyReplicateChannel channel;

    public ReplicatorServerHandler(SequenceGroups groups) {
        this.groups = groups;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = new NettyReplicateChannel(ctx.channel());
        this.replica = new Replica(channel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ReplicatorCommand cmd) throws Exception {
        try {
            switch (cmd.getType()) {
                case HANDSHAKE:
                    String topic = cmd.getTopic();

                    Sequence seq = groups.getSequence(topic);
                    if (seq == null) {
                        channel.writeError(ReplicatorError.ETOPIC_NOT_FOUND);
                    }

                    replica.onStart(topic, seq);
                    break;
                case GET:
                    long fromIndex = cmd.getFromIndex();
                    int limit = cmd.getLimit();

                    replica.heandleSyncLogs(fromIndex, limit);
                    break;
                case SNAPSHOT:
                    replica.handleSyncSnapshot();
                    break;
            }
        } catch (ReplicatorException ex) {
            channel.writeError(ex.getError());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReplicatorException) {
            channel.writeError(((ReplicatorException)cause).getError());
        } else {
            logger.error("Got unexpected exception", cause);
            channel.writeError(ReplicatorError.UNKNOWN);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
        }
    }
}
