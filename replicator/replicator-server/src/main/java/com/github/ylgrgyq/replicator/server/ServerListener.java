package com.github.ylgrgyq.replicator.server;

public interface ServerListener {
    void serverStarting(ReplicatorServer server) throws Exception;

    void serverStarted(ReplicatorServer server) throws Exception;

    void serverStoping(ReplicatorServer server) throws Exception;

    void serverStopped(ReplicatorServer server) throws Exception;
}
