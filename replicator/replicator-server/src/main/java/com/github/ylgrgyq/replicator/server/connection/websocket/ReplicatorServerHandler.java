package com.github.ylgrgyq.replicator.server.connection.websocket;

import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.proto.HandshakeRequest;
import com.github.ylgrgyq.replicator.server.Replica;
import com.github.ylgrgyq.replicator.server.ReplicatorError;
import com.github.ylgrgyq.replicator.server.ReplicatorException;
import com.github.ylgrgyq.replicator.server.sequence.Sequence;
import com.github.ylgrgyq.replicator.server.sequence.SequenceGroups;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerHandler extends SimpleChannelInboundHandler<RemotingCommand> {
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
    protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand cmd) throws Exception {
        try {
            switch (cmd.getMessageType()) {
                case HANDSHAKE:
                    HandshakeRequest handshake = cmd.getBody();
                    String topic = handshake.getTopic();

                    logger.info("handshake on topic: {} {}", topic, cmd);

                    Sequence seq = groups.getSequence(topic);
                    if (seq == null) {
                        channel.writeError(ReplicatorError.ETOPIC_NOT_FOUND);
                    }

                    replica.onStart(topic, seq, cmd);
                    break;
                case FETCH_LOGS:
                    replica.handleSyncLogs(cmd);
                    break;
                case FETCH_SNAPSHOT:
                    replica.handleFetchSnapshot(cmd);
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
