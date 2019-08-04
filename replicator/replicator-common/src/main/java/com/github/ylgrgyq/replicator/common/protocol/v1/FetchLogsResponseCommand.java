package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.proto.FetchLogsResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@CommandFactoryManager.AutoLoad
public final class FetchLogsResponseCommand extends ResponseCommandV1 {
    FetchLogsResponseCommand() {
        super(MessageType.FETCH_LOGS);
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
                Message req = FetchLogsResponse.parseFrom(content);
                setResponseObject(req);
            }
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}
