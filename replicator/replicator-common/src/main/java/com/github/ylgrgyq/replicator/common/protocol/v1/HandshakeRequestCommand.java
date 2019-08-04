package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.proto.HandshakeRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@CommandFactoryManager.AutoLoad
public final class HandshakeRequestCommand extends RequestCommandV1 {
    HandshakeRequestCommand() {
        super(MessageType.HANDSHAKE);
    }

    @Override
    public void serialize() {
        Message req = getRequestObject();
        if (req != null) {
            setContent(req.toByteArray());
        }
    }

    @Override
    public void deserialize() throws DeserializationException {
        try {
            byte[] content = getContent();

            if (content != null) {
                Message req = HandshakeRequest.parseFrom(content);

                setRequestObject(req);
            }
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}
