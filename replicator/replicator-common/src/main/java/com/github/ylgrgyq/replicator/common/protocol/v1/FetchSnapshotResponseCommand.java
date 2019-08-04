package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.common.exception.SerializationException;
import com.github.ylgrgyq.replicator.proto.FetchSnapshotResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@CommandFactoryManager.AutoLoad
public final class FetchSnapshotResponseCommand extends ResponseCommandV1 {
    static {
        CommandFactoryManager.registerResponseCommand(MessageType.FETCH_SNAPSHOT, FetchSnapshotResponseCommand::new);
    }

    public FetchSnapshotResponseCommand() {
        super(MessageType.FETCH_SNAPSHOT);
    }

    @Override
    public void serialize() throws SerializationException {
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
                Message req = FetchSnapshotResponse.parseFrom(content);
                setResponseObject(req);
            }

        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}
