package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.proto.*;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

public class ReplicatorSerializer implements Serializer {
    @Override
    public <T extends RequestCommand> boolean serialize(T cmd) {
        Message req = cmd.getRequestObject();
        if (req != null) {
            cmd.setContent(req.toByteArray());
        }
        return true;
    }

    @Override
    public <T extends RequestCommand> boolean deserialize(T cmd) throws CodecException{
        try {
            byte[] content = cmd.getContent();

            Message req = null;
            switch (cmd.getMessageType()) {
                case HANDSHAKE:
                    if (content != null) {
                        req = HandshakeRequest.parseFrom(content);
                    }
                    break;
                case FETCH_LOGS:
                    if (content != null) {
                        req = FetchLogsRequest.parseFrom(content);
                    }
                    break;
                case ERROR:
                    if (content != null) {
                        req = ErrorInfo.parseFrom(content);
                    }
                    break;
                case FETCH_SNAPSHOT:
                    if (content != null) {
                        req = FetchSnapshotRequest.parseFrom(content);
                    }
                    break;
            }

            cmd.setRequestObject(req);
            return true;
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize request command failed", ex);
        }
    }


    @Override
    public <T extends ResponseCommand> boolean serialize(T cmd) {
        Message res = cmd.getResponseObject();
        if (res != null) {
            cmd.setContent(res.toByteArray());
        }
        return true;
    }

    @Override
    public <T extends ResponseCommand> boolean deserialize(T cmd) throws CodecException {
        try {
            byte[] content = cmd.getContent();

            Message req = null;
            switch (cmd.getMessageType()) {
                case HANDSHAKE:
                    if (content != null) {
                        req = HandshakeResponse.parseFrom(content);
                    }
                    break;
                case FETCH_LOGS:
                    if (content != null) {
                        req = FetchLogsResponse.parseFrom(content);
                    }
                    break;
                case FETCH_SNAPSHOT:
                    if (content != null) {
                        req = FetchSnapshotResponse.parseFrom(content);
                    }
                    break;
            }

            cmd.setResponseObject(req);
            return true;
        } catch (InvalidProtocolBufferException ex) {
            throw new DeserializationException("Deserialize response command failed", ex);
        }
    }
}
