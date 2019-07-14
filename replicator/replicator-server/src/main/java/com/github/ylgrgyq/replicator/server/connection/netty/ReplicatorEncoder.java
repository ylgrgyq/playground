package com.github.ylgrgyq.replicator.server.connection.netty;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ReplicatorEncoder extends MessageToMessageEncoder<ReplicatorCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ReplicatorCommand o, List<Object> list) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();
        o.writeTo(new ByteBufOutputStream(buf));

        list.add(new BinaryWebSocketFrame(buf));
    }
}
