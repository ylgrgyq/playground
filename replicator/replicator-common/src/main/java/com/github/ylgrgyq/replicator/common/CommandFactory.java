package com.github.ylgrgyq.replicator.common;

public class CommandFactory {
    public static ResponseCommand createResponse(RemotingCommand request) {
        ResponseCommand res = new ResponseCommand();
        res.setMessageVersion(request.getMessageVersion());
        res.setMessageType(request.getMessageType());

        return res;
    }

    public static RequestCommand createRequest() {
        RequestCommand req = new RequestCommand();
        req.setMessageVersion(MessageType.VERSION);
        return req;
    }
}
