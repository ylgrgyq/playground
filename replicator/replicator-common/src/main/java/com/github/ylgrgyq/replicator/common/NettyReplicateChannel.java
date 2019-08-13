package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.commands.ErrorCommand;
import com.github.ylgrgyq.replicator.common.commands.RemotingCommand;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class NettyReplicateChannel implements ReplicateChannel {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

    private Channel socket;

    public NettyReplicateChannel(Channel socket) {
        this.socket = socket;
    }


    public void writeRemoting(RemotingCommand cmd) {
        socket.writeAndFlush(cmd);
    }

    public void writeError(ReplicatorError error) {
        ErrorCommand errorCommand = new ErrorCommand();
        errorCommand.setErrorCode(error.getErrorCode());
        errorCommand.setErrorMsg(error.getMsg());

        logger.debug("send error {}", errorCommand);
        socket.writeAndFlush(errorCommand);
    }

    @Override
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        socket.close().addListener(f ->
                future.complete(null)
        );
        return future;
    }
}
