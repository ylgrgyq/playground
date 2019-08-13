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
    private final ConcurrentMap<String, SequenceImpl> topicToSource;
    private final ReplicatorServerOptions replicatorServerOptions;

    public SequenceGroups(ReplicatorServerOptions replicatorServerOptions) {
        this.topicToSource = new ConcurrentHashMap<>();
        this.replicatorServerOptions = replicatorServerOptions;
    }

    public SequenceImpl getOrCreateSequence(String topic, Storage<? extends StorageHandle> storage, SequenceOptions options) {
        SequenceImpl sequence = topicToSource.get(topic);
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

    private SequenceImpl createSequence(String topic, Storage<? extends StorageHandle> storage, SequenceOptions options){
        SequenceStorage sequenceStorage = storage.createSequenceStorage(topic, options);
        ScheduledExecutorService executor = replicatorServerOptions.getWorkerScheduledExecutor();
        return new SequenceImpl(executor, sequenceStorage, options);
    }

    public synchronized void dropSequence(String topic) {
        SequenceImpl seq = topicToSource.remove(topic);
        if (seq != null) {
            seq.drop();

        }
    }

    public SequenceImpl getSequence(String topic) {
        return topicToSource.get(topic);
    }

    public void shutdownAllSequences() {
        for (Map.Entry<String, SequenceImpl> entry : topicToSource.entrySet()){
            SequenceImpl seq = entry.getValue();
            seq.shutdown();
        }
    }
}
