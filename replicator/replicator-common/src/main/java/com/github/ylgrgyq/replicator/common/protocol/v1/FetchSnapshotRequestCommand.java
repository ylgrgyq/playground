package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.common.exception.SerializationException;
import com.github.ylgrgyq.replicator.proto.FetchSnapshotRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@CommandFactoryManager.AutoLoad
public final class FetchSnapshotRequestCommand extends RequestCommandV1 {
    static {
        CommandFactoryManager.registerRequestCommand(MessageType.FETCH_SNAPSHOT, FetchSnapshotRequestCommand::new);
    }

    public FetchSnapshotRequestCommand() {
        super(MessageType.FETCH_SNAPSHOT);
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

            if (content != null) {
                Message req = FetchSnapshotRequest.parseFrom(content);
                setRequestObject(req);
            }

        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }
}
