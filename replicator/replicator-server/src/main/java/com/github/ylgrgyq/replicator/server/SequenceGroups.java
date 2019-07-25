package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SequenceGroups {
    private final ConcurrentMap<String, Sequence> topicToSource;

    public SequenceGroups() {
        this.topicToSource = new ConcurrentHashMap<>();
    }

    public Sequence getOrCreateSequence(String topic, Storage<?> storage, SequenceOptions options) {

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

    private Sequence createSequence(String topic, Storage<?> storage, SequenceOptions options){
        SequenceStorage sequenceStorage = storage.createSequenceStorage(topic, options);
        if (sequenceStorage == null) {
            throw new ReplicatorException(ReplicatorError.EINTERNAL_ERROR);
        }

        Sequence sequence = new Sequence(topic, sequenceStorage, options);
        sequence.init();
        return sequence;
    }

    public synchronized boolean deleteSequence(String topic) {
        return topicToSource.remove(topic) != null;
    }

    public synchronized Sequence replaceSequence(String topic, Storage<?> storage, SequenceOptions options) {
        Sequence sequence = createSequence(topic, storage, options);

        topicToSource.put(topic, sequence);

        return sequence;
    }

    public Sequence getSequence(String topic) {
        return topicToSource.get(topic);
    }

    public void shutdown(){
        // todo close every sequence
    }
}
