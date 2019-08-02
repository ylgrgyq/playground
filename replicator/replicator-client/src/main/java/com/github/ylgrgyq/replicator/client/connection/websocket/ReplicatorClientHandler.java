package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.*;
import com.github.ylgrgyq.replicator.common.*;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorClientHandler extends SimpleChannelInboundHandler<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientHandler.class);

    private ReplicatorClientImpl client;

    public ReplicatorClientHandler(ReplicatorClientImpl client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand cmd) {
        client.onReceiveRemotingMsg(cmd);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReplicatorException) {
            logger.error("Replicator error", ((ReplicatorException) cause).getError());
        } else {
            logger.error("Got unexpected exception", cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            client.onRetryFetchLogs();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        client.onChannelActive(new NettyReplicateChannel(ctx.channel()));

    }
}
