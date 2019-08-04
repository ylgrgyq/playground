package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.RequestCommand;

abstract class RequestCommandV1 extends RequestCommand {
    RequestCommandV1(MessageType type) {
        super(type, MessageType.VERSION);
    }

    RequestCommandV1(CommandType commandType, MessageType msgType) {
        super(commandType, msgType, MessageType.VERSION);
    }
}
