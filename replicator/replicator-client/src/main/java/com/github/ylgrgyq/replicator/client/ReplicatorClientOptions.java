package com.github.ylgrgyq.replicator.client;

import java.net.URI;

public class ReplicatorClientOptions {

    private int port = 8888;
    private String host = "localhost";
    private long reconnectDelaySeconds = 10;
    private URI uri;
    private int pendingFlushLogsLowWaterMark = 10;

    public ReplicatorClientOptions() {
        try {
            uri = new URI("ws://127.0.0.1:8888");
        } catch (Exception ex) {
            // ignore by now
        }
    }

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

    public URI getUri() {
        return uri;
    }

    public int getPendingFlushLogsLowWaterMark() {
        return pendingFlushLogsLowWaterMark;
    }
}
