package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.ResponseCommand;

abstract class ResponseCommandV1 extends ResponseCommand {
    ResponseCommandV1(MessageType type) {
        super(type, MessageType.VERSION);
    }
}
