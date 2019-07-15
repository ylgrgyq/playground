package com.github.ylgrgyq.replicator.client;

public class ReplicatorClientOptions {

    private int port = 8888;
    private String host = "localhost";
    private long reconnectDelaySeconds = 10;

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public void setReconnectDelaySeconds(long reconnectDelaySeconds) {
        this.reconnectDelaySeconds = reconnectDelaySeconds;
    }
}
