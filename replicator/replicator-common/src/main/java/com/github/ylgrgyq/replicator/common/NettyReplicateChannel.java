package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.proto.ErrorInfo;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyReplicateChannel implements ReplicateChannel{
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

    private Channel socket;
    public NettyReplicateChannel(Channel socket) {
        this.socket = socket;
    }


    public void writeRemoting(RemotingCommand cmd) {
        socket.writeAndFlush(cmd);
    }

    public void writeError(ReplicatorError error) {
        RequestCommand req = CommandFactory.createRequest();
        req.setMessageType(MessageType.ERROR);
        req.setCommandType(CommandType.ONE_WAY);

        ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
        errorInfo.setErrorCode(error.getErrorCode());
        errorInfo.setErrorMsg(error.getMsg());

        req.setContent(errorInfo.build().toByteArray());

        logger.debug("send error {}", errorInfo);
        socket.writeAndFlush(req);
    }

    @Override
    public void close() {
        if (socket.isActive()) {
            socket.close();
        }
    }
}
