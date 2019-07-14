package com.github.ylgrgyq.replicator.server.connection.netty;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.github.ylgrgyq.replicator.server.*;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
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
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        ReplicatorCommand cmd;
        try {
            ByteBufInputStream stream = new ByteBufInputStream(frame.content());
            cmd = ReplicatorCommand.parseFrom(stream);
        } catch (InvalidProtocolBufferException ex) {
            ctx.channel().write(ReplicatorError.EUNKNOWNPROTOCOL);
            return;
        }

        try {
            switch (cmd.getType()) {
                case HANDSHAKE:
                    String topic = cmd.getTopic();

                    Sequence seq = groups.getSequence(topic);
                    if (seq == null) {
                        seq = groups.createSequence(topic, new SequenceOptions());
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
}
