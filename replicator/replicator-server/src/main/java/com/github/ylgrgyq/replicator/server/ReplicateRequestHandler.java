package com.github.ylgrgyq.replicator.server;

public interface ReplicateRequestHandler {
    void onStart(String topic, Sequence seq);
    void heandleSyncLogs(long fromIndex, int limit);
    void handleSyncSnapshot();
    void onFinish();
}
