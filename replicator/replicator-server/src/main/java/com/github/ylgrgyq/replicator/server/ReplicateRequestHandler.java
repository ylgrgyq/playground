package com.github.ylgrgyq.replicator.server;

public interface ReplicateRequestHandler {
    void onStart(SequenceGroups groups, String topic);
    void heandleSyncLogs(long fromIndex, int limit);
    void handleSyncSnapshot();
    void onFinish();
}
