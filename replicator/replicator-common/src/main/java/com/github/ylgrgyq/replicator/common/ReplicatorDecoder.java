package com.github.ylgrgyq.replicator.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ReplicatorDecoder extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorDecoder.class);

    private static final int lessLen = Math.min(Protocol.getResponseHeaderLength(), Protocol.getRequestHeaderLength());

    private Serializer serializer;

    public ReplicatorDecoder() {
        this.serializer = new ReplicatorSerializer();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // the less length between response header and request header
        if (in.readableBytes() >= lessLen) {
            checkProtocol(in);

            in.markReaderIndex();
            in.readByte(); // magic
            in.readByte(); // protocol version
            byte commandType = in.readByte(); // command type
            if (commandType == CommandType.REQUEST.getCode() || commandType == CommandType.ONE_WAY.getCode()) {
                decodeRequest(commandType, in, out);
            } else if (commandType == CommandType.RESPONSE.getCode()) {
                decodeResponse(in, out);
            } else {
                String emsg = "Unknown command type: " + commandType;
                logger.error(emsg);
                throw new RuntimeException(emsg);
            }
        }
    }

    private void checkProtocol(ByteBuf in) {
        in.markReaderIndex();
        byte magic = in.readByte();
        byte protocolVersion = in.readByte();
        in.resetReaderIndex();
        if (magic != Protocol.PROTOCOL_MAGIC) {
            String emsg = "Unknown protocol MAGIC: " + magic;
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }

        if (protocolVersion != Protocol.PROTOCOL_VERSION) {
            String emsg = "Unknown protocol version: " + protocolVersion;
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }
    }

    private void decodeRequest(byte commandType, ByteBuf in, List<Object> out) throws CodecException {
        if (in.readableBytes() >= Protocol.getRequestHeaderLength() - 3) {
            byte msgTypeCode = in.readByte();
            byte msgVersion = in.readByte();
            int contentLen = in.readInt();
            byte[] content = null;

            if (in.readableBytes() >= contentLen) {
                if (contentLen > 0) {
                    content = new byte[contentLen];
                    in.readBytes(content);
                }
            } else {// not enough data
                in.resetReaderIndex();
                return;
            }

            RequestCommand command = new RequestCommand();
            command.setCommandType(CommandType.REQUEST.getCode() == commandType ? CommandType.REQUEST : CommandType.ONE_WAY);
            command.setMessageType(MessageType.findMessageTypeByCode(msgTypeCode));
            command.setMessageVersion(msgVersion);
            command.setContent(content);

            logger.info("request {}", msgTypeCode, content);
            serializer.deserialize(command);

            out.add(command);
        } else {
            in.resetReaderIndex();
        }
    }

    private void decodeResponse(ByteBuf in, List<Object> out) throws CodecException {
        if (in.readableBytes() >= Protocol.getResponseHeaderLength() - 3) {
            byte msgTypeCode = in.readByte();
            byte msgVersion = in.readByte();
            int contentLen = in.readInt();
            byte[] content = null;

            // continue read
            if (in.readableBytes() >= contentLen) {
                if (contentLen > 0) {
                    content = new byte[contentLen];
                    in.readBytes(content);
                }
            } else {// not enough data
                in.resetReaderIndex();
                return;
            }

            ResponseCommand command = new ResponseCommand();
            command.setMessageType(MessageType.findMessageTypeByCode(msgTypeCode));
            command.setMessageVersion(msgVersion);
            command.setContent(content);
            logger.info("response {}", msgTypeCode, content);

            serializer.deserialize(command);

            out.add(command);
        } else {
            in.resetReaderIndex();
        }
    }
}
