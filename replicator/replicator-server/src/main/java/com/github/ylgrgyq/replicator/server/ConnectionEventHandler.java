package com.github.ylgrgyq.replicator.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Sharable
public class ConnectionEventHandler extends ChannelInboundHandlerAdapter {
    private final Set<Channel> childChannels;
    private final Set<Channel> unmodifiableChildChannels;
    private final AtomicInteger numConnections;

    public ConnectionEventHandler() {
        this.childChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.unmodifiableChildChannels = Collections.unmodifiableSet(childChannels);
        this.numConnections = new AtomicInteger();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Channel child = (Channel) msg;

        childChannels.add(child);
        numConnections.incrementAndGet();
        child.closeFuture().addListener(future -> {
           childChannels.remove(child);
           numConnections.decrementAndGet();
        });

        super.channelRead(ctx, msg);
    }

    /**
     * Returns the number of open connections.
     */
    public int numConnections() {
        return numConnections.get();
    }

    /**
     * Returns the immutable set of child {@link Channel}s.
     */
    public Set<Channel> children() {
        return unmodifiableChildChannels;
    }
}
