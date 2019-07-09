package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.proto.*;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Subscriber {
    private static final Logger logger = LoggerFactory.getLogger(Subscriber.class);

    private StateMachine stateMachine;
    private WebSocket socket;
    private long lastIndex;

    public Subscriber(WebSocket socket, StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.socket = socket;
        this.lastIndex = -1;
        socket.exceptionHandler(t -> logger.error("subscriber socket broken", t));
        socket.closeHandler(none -> logger.error("subscriber socket closed"));
    }

    public void subscribe(String topic) {
        socket.binaryMessageHandler(buffer -> {
            try {
                logger.info("receive bytes {}", buffer.getBytes().length);
                ReplicatorCommand cmd = ReplicatorCommand.parseFrom(buffer.getBytes());
                switch (cmd.getType()) {
                    case GET_RESP:
                        SyncLogEntries logs = cmd.getLogs();
                        logger.info("GET RESP {}", logs);
                        List<LogEntry> entryList = logs.getEntriesList();
                        List<byte[]> entris = new ArrayList<>(entryList.size());
                        if (entryList.isEmpty()) {
                            logger.info("GET RESP return empty");
                        } else {
                            LogEntry firstEntry = entryList.get(0);
                            if (lastIndex + 1 == firstEntry.getIndex()) {
                                for (LogEntry entry : entryList) {
                                    entris.add(entry.getData().toByteArray());
                                    lastIndex = entry.getIndex();
                                }
                                stateMachine.apply(entris);
                                requestLogs(topic, lastIndex);
                            } else {
                                requestSnapshot(topic);
                            }
                        }
                        break;
                    case SNAPSHOT_RESP:
                        Snapshot snapshot = cmd.getSnapshot();
                        stateMachine.snapshot(snapshot.toByteArray());
                        requestLogs(topic, snapshot.getId());
                        break;
                    case ERROR:
                        ErrorInfo errorInfo = cmd.getError();
                        logger.info("got error {}", errorInfo);
                        if (errorInfo.getErrorCode() == 10001) {
                            requestSnapshot(topic);
                        } else {
                            logger.error("got error", errorInfo);
                        }
                        break;

                }
            } catch (InvalidProtocolBufferException ex) {
                logger.error("unknown protocol", ex);
            }
        });

        requestSnapshot(topic);
    }

    private void requestLogs(String topic, long fromIndex) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.GET);
        get.setTopic(topic);
        get.setFromIndex(fromIndex);
        get.setLimit(100);
        get.build();

        Buffer buf = Buffer.buffer(get.build().toByteArray());
        socket.write(buf);
    }

    private void requestSnapshot(String topic) {
        ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
        get.setType(ReplicatorCommand.CommandType.SNAPSHOT);
        get.setTopic(topic);
        Buffer buf = Buffer.buffer(get.build().toByteArray());

        socket.write(buf);
    }
}
