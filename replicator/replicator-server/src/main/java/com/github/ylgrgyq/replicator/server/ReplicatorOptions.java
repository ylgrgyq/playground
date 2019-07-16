package com.github.ylgrgyq.replicator.server;

public class ReplicatorOptions {

    private int port = 8080;
    private String host = "localhost";

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
}
