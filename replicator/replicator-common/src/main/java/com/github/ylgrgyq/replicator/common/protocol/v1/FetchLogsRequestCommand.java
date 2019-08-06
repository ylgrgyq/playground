package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.entity.FetchLogsRequest;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

@CommandFactoryManager.AutoLoad
public final class FetchLogsRequestCommand extends RequestCommandV1 {
    private FetchLogsRequest body;

    public FetchLogsRequestCommand() {
        super(MessageType.FETCH_LOGS);
    }

    @Override
    public void serialize() {
        setContent(body.serialize());
    }

    @Override
    public void deserialize() throws DeserializationException {
        byte[] content = getContent();
        body = new FetchLogsRequest();
        body.deserialize(content);
    }
}
