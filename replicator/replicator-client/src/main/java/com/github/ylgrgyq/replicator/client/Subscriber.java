package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.proto.*;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Subscriber {
    private static final Logger logger = LoggerFactory.getLogger(Subscriber.class);

    private StateMachine stateMachine;
    private NetSocket socket;

    public Subscriber(NetSocket socket, StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.socket = socket;
        socket.exceptionHandler(t -> logger.error("subscriber socket broken", t));
        socket.closeHandler(none -> logger.error("subscriber socket closed"));
    }

    public void subscribe(String topic){
        socket.handler(buffer -> {
            try {
                ReplicatorCommand cmd = ReplicatorCommand.parseFrom(buffer.getBytes());
                switch (cmd.getType()){
                    case GET_RESP:
                        SyncLogEntries logs = cmd.getLogs();
                        List<LogEntry> entryList = logs.getEntriesList();
                        List<byte[]> entris = new ArrayList<>(entryList.size());
                        long lastIndex = -1;
                        for (LogEntry entry : entryList) {
                            entris.add(entry.getData().toByteArray());
                            lastIndex = entry.getIndex();
                        }
                        stateMachine.apply(entris);
                        requestLogs(lastIndex);
                        break;
                    case SNAPSHOT_RESP:
                        Snapshot snapshot = cmd.getSnapshot();
                        stateMachine.snapshot(snapshot.toByteArray());
                        requestLogs(snapshot.getIndex());
                        break;
                    case ERROR:
                        ErrorInfo errorInfo = cmd.getError();
                        if (errorInfo.getErrorCode() == 10001){
                            requestSnapshot();
                        } else {
                            logger.error("got error", errorInfo);
                        }
                        break;

                }
            }catch (InvalidProtocolBufferException ex) {
                logger.error("unknown protocol", ex);
            }

        });
        requestLogs(-1);
    }

    private void requestLogs(long fromIndex) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.GET);
        get.setFromIndex(fromIndex);
        get.setLimit(100);
        get.build();

        Buffer buf = Buffer.buffer(get.build().toByteArray());
        socket.write(buf, ret -> {
            if (! ret.succeeded()) {
                logger.error("get snapshot failed");
            }
        });
    }

    private void requestSnapshot() {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.SNAPSHOT);
        Buffer buf = Buffer.buffer(get.build().toByteArray());

        socket.write(buf, ret -> {
            if (! ret.succeeded()) {
                logger.error("get snapshot failed");
            }
        });
    }
}
