package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.CommandFactory;
import com.github.ylgrgyq.replicator.common.RemotingCommand;
import com.github.ylgrgyq.replicator.common.exception.CodecException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ReplicatorDecoder extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorDecoder.class);

    private static final int lessLen = Math.min(Protocol.getResponseHeaderLength(), Protocol.getRequestHeaderLength());

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
                decodeRequest(in, out);
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

    private void decodeRequest(ByteBuf in, List<Object> out) throws CodecException {
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

            CommandFactory factory = getRequestCommandFactory(msgTypeCode);
            RemotingCommand command = factory.createCommand();
            command.setMessageVersion(msgVersion);
            command.setContent(content);
            command.deserialize();

            if (logger.isDebugEnabled()) {
                logger.info("receive request {} {}", MessageType.findMessageTypeByCode(msgTypeCode), command.getBody());
            }

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

            CommandFactory factory = getResponseCommandFactory(msgTypeCode);
            RemotingCommand command = factory.createCommand();
            command.setMessageVersion(msgVersion);
            command.setContent(content);
            command.deserialize();

            if (logger.isDebugEnabled()) {
                logger.info("response {} {}", MessageType.findMessageTypeByCode(msgTypeCode), command.getBody());
            }

            out.add(command);
        } else {
            in.resetReaderIndex();
        }
    }

    private CommandFactory getRequestCommandFactory(byte msgTypeCode) {
        MessageType msgType = getMessageType(msgTypeCode);

        CommandFactory factory = CommandFactoryManager.getRequestCommandFactory(msgType);
        if (factory == null) {
            String emsg = "No request command factory registered for message type: " + msgType.name();
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }

        return factory;
    }

    private CommandFactory getResponseCommandFactory(byte msgTypeCode) {
        MessageType msgType = getMessageType(msgTypeCode);

        CommandFactory factory = CommandFactoryManager.getResponseCommandFactory(msgType);
        if (factory == null) {
            String emsg = "No response command factory registered for message type: " + msgType.name();
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }

        return factory;
    }

    private MessageType getMessageType(byte msgTypeCode) {
        MessageType msgType = MessageType.findMessageTypeByCode(msgTypeCode);
        if (msgType == null) {
            String emsg = "Unknown message type for message type code: " + msgTypeCode;
            logger.error(emsg);
            throw new RuntimeException(emsg);
        }
        return msgType;
    }
}
