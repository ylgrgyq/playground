package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.proto.HandshakeResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@CommandFactoryManager.AutoLoad
public final class HandshakeResponseCommand extends ResponseCommandV1 {
    HandshakeResponseCommand() {
        super(MessageType.HANDSHAKE);
    }

    @Override
    public void serialize() {
        Message req = getResponseObject();
        if (req != null) {
            setContent(req.toByteArray());
        }
    }

    @Override
    public void deserialize() throws DeserializationException {
        try {
            byte[] content = getContent();

            if (content != null) {
                Message req = HandshakeResponse.parseFrom(content);

                setResponseObject(req);
            }
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}