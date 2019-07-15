package com.github.ylgrgyq.replicator.client.connection.websocket;

import com.github.ylgrgyq.replicator.client.ReplicatorError;
import com.github.ylgrgyq.replicator.client.ReplicatorException;
import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;

@ChannelHandler.Sharable
public class ReplicatorDecoder extends MessageToMessageDecoder<WebSocketFrame> {
    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        ReplicatorCommand cmd;
        try {
            ByteBufInputStream stream = new ByteBufInputStream(frame.content());
            cmd = ReplicatorCommand.parseFrom(stream);
            out.add(cmd);
        } catch (InvalidProtocolBufferException ex) {
            throw new ReplicatorException(ReplicatorError.EUNKNOWNPROTOCOL);
        }
    }
}
