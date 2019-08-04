package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.common.exception.SerializationException;
import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandFactoryManager.AutoLoad
public final class ErrorCommand extends RequestCommandV1 {
    private static final Logger logger = LoggerFactory.getLogger("protocol-v1");

    static {
        logger.warn("load error command");
        CommandFactoryManager.registerRequestCommand(MessageType.ERROR, ErrorCommand::new);
    }

    public ErrorCommand() {
        super(CommandType.ONE_WAY, MessageType.ERROR);
    }

    @Override
    public void serialize() throws SerializationException {
        Message req = getRequestObject();
        if (req != null) {
            setContent(req.toByteArray());
        }
    }

    @Override
    public void deserialize() throws DeserializationException {
        try {
            byte[] content = getContent();

            Message req = ErrorInfo.parseFrom(content);

            setRequestObject(req);
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}
