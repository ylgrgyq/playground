package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.server.ReplicatorError;
import com.github.ylgrgyq.replicator.server.ReplicatorException;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.github.ylgrgyq.replicator.server.storage.StorageHandle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SequenceGroups {
    private final ConcurrentMap<String, Sequence> topicToSource;

    public SequenceGroups() {
        this.topicToSource = new ConcurrentHashMap<>();
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
        if (sequenceStorage == null) {
            throw new ReplicatorException(ReplicatorError.EINTERNAL_ERROR);
        }

        return new Sequence(topic, sequenceStorage, options);
    }

    public synchronized boolean deleteSequence(String topic) {
        Sequence seq = topicToSource.remove(topic);
        if (seq != null) {
            seq.shutdown();
            return true;
        } else {
            return false;
        }
    }

    public synchronized Sequence replaceSequence(String topic, Storage<? extends StorageHandle> storage, SequenceOptions options) {
        Sequence sequence = createSequence(topic, storage, options);

        Sequence oldSeq = topicToSource.put(topic, sequence);
        if (oldSeq != null) {
            oldSeq.shutdown();
        }

        return sequence;
    }

    public Sequence getSequence(String topic) {
        return topicToSource.get(topic);
    }

    public void shutdown(){
        for (Map.Entry<String, Sequence> entry : topicToSource.entrySet()){
            Sequence seq = entry.getValue();
            seq.shutdown();
        }
    }
}
