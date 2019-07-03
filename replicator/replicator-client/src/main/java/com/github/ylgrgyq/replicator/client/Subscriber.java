package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.proto.ReplicatorCommand;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subscriber {
    private static final Logger logger = LoggerFactory.getLogger(Subscriber.class);

    private StateMachine stateMachine;
    private NetClient client;

    public Subscriber(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public void subscribe(String topic, NetClient client){
        NetClientOptions options = new NetClientOptions();

        client.connect(8888, "127.0.0.1", ret -> {
            if (ret.succeeded()) {
                createWorker(ret.result());
            } else {
                logger.error("connect failed", ret.cause());

            }
        });

    }

    private Thread createWorker(NetSocket socket) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                ReplicatorCommand.Builder get = ReplicatorCommand.newBuilder();
                get.setType(ReplicatorCommand.CommandType.GET);
                get.setFromIndex(-1);
                get.setLimit(100);
                get.build();

                socket
            }
        });
    }

    private void getSnapshot(NetSocket socket) {
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
