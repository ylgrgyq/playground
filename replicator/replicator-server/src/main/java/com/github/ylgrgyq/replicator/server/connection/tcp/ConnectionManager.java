package com.github.ylgrgyq.replicator.server.connection.tcp;

import com.github.ylgrgyq.replicator.server.Replica;
import io.netty.channel.ChannelId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private Map<ChannelId, Replica> connections;
    private volatile boolean stop;

    public ConnectionManager() {
        this.connections = new ConcurrentHashMap<>();
    }

    public boolean registerConnection(ChannelId channelId, Replica replica) {
        if (stop) {
            return false;
        }

        connections.put(channelId, replica);
        return true;
    }

    public void deregisterConnection(ChannelId channelId) {
        connections.remove(channelId);
    }

    public void shutdown() {
        if (stop) {
            return;
        }

        stop = true;
        for(Map.Entry<ChannelId, Replica> entry: connections.entrySet()) {
            Replica replica = entry.getValue();
            replica.onFinish();
        }
    }
}
