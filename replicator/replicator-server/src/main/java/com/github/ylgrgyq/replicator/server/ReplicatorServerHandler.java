package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.common.NettyReplicateChannel;
import com.github.ylgrgyq.replicator.common.ReplicatorError;
import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerHandler extends SimpleChannelInboundHandler<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServerHandler.class);
    private final ReplicatorServer server;
    private Replica replica;
    private NettyReplicateChannel channel;

    public ReplicatorServerHandler(ReplicatorServer server) {
        this.server = server;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        Channel ch = ctx.channel();
        this.channel = new NettyReplicateChannel(ch);
        this.replica = new Replica(channel);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        replica.onFinish();

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand cmd) throws Exception {
        server.onReceiveRemotingCommand(channel, replica, cmd);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReplicatorException) {
            channel.writeError(((ReplicatorException) cause).getError());
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
