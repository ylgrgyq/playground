package com.github.ylgrgyq.replicator.client;

public interface ClientListener {
    void clientStarting(ReplicatorClient client) throws Exception;

    void clientStarted(ReplicatorClient client) throws Exception;

    void clientStopping(ReplicatorClient client) throws Exception;

    void clientStopped(ReplicatorClient client) throws Exception;
}
