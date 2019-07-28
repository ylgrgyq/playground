package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.server.ReplicatorServerOptions;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageHandle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

public class SequenceGroups {
    private final ConcurrentMap<String, Sequence> topicToSource;
    private final ReplicatorServerOptions replicatorServerOptions;

    public SequenceGroups(ReplicatorServerOptions replicatorServerOptions) {
        this.topicToSource = new ConcurrentHashMap<>();
        this.replicatorServerOptions = replicatorServerOptions;
    }

    public Sequence getOrCreateSequence(String topic, Storage<? extends StorageHandle> storage, SequenceOptions options) {
        Sequence sequence = topicToSource.get(topic);
        if (sequence == null) {
            synchronized (this) {
                sequence = topicToSource.get(topic);
                if (sequence == null) {
                    sequence = createSequence(topic, storage, options);
                    topicToSource.put(topic, sequence);
                }
            }
        }

        return sequence;
    }

    private Sequence createSequence(String topic, Storage<? extends StorageHandle> storage, SequenceOptions options){
        SequenceStorage sequenceStorage = storage.createSequenceStorage(topic, options);
        ScheduledExecutorService executor = replicatorServerOptions.getWorkerScheduledExecutor();
        return new Sequence(topic, executor, sequenceStorage, options);
    }

    public synchronized void dropSequence(String topic) {
        Sequence seq = topicToSource.remove(topic);
        if (seq != null) {
            seq.drop();

        }
    }

    public Sequence getSequence(String topic) {
        return topicToSource.get(topic);
    }

    public void shutdownAllSequences() throws InterruptedException {
        for (Map.Entry<String, Sequence> entry : topicToSource.entrySet()){
            Sequence seq = entry.getValue();
            seq.shutdown();
        }
    }
}
