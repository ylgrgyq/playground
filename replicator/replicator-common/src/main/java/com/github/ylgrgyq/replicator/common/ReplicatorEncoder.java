package com.github.ylgrgyq.replicator.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorEncoder extends MessageToByteEncoder<RemotingCommand> {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorEncoder.class);

    private Serializer serializer;

    public ReplicatorEncoder() {
        this.serializer = new ReplicatorSerializer();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingCommand msg, ByteBuf out) throws Exception {
        try {
            /*
             * magic
             * proto version
             * command type: request/response/request oneway
             * message type: code for command
             * message version: version for message type
             * content length: length of content
             * content
             */
            out.writeByte(Protocol.PROTOCOL_MAGIC);
            out.writeByte(Protocol.PROTOCOL_VERSION);
            out.writeByte(msg.getCommandType().getCode());
            out.writeByte(msg.getMessageType().getCode());
            out.writeByte(MessageType.VERSION);

            switch (msg.getCommandType()) {
                case REQUEST:
                case ONE_WAY:
                    logger.info("Send request {} {}", msg.getMessageType().name(), msg.getBody());
                    serializer.serialize((RequestCommand) msg);
                    break;
                case RESPONSE:
                    logger.info("Send response {} {}", msg.getMessageType().name(), msg.getBody());
                    serializer.serialize((ResponseCommand) msg);
                    break;
            }

            out.writeInt(msg.getContentLength());
            if (msg.getContentLength() > 0) {
                out.writeBytes(msg.getContent());
            }
        } catch (Exception e) {
            logger.error("Exception caught!", e);
            throw e;
        }
    }
}
