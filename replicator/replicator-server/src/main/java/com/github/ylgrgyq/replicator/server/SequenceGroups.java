package com.github.ylgrgyq.replicator.server;

import java.util.HashMap;
import java.util.Map;

public class SequenceGroups {
    private Map<String, Sequence> topicToSource;

    public SequenceGroups() {
        this.topicToSource = new HashMap<>();
    }

    public synchronized Sequence createSequence(String topic, SequenceOptions options) {
        Sequence sequence = new Sequence(topic, options);
        Sequence oldSequence = topicToSource.computeIfAbsent(topic, k -> sequence);

        if (sequence == oldSequence) {
            sequence.init();
        }

        return oldSequence;
    }

    public synchronized boolean deleteSequence(String topic) {
        return topicToSource.remove(topic) != null;
    }

    public synchronized Sequence replaceSnapshotGenerator(String topic, SequenceOptions options) {
        Sequence sequence = new Sequence(topic, options);
        sequence.init();

        topicToSource.put(topic, sequence);

        return sequence;
    }

    public synchronized Sequence getSequence(String topic) {
        return topicToSource.get(topic);
    }
}
