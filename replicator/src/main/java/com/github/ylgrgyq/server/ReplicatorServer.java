package com.github.ylgrgyq.server;

import com.github.ylgrgyq.server.storage.Storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReplicatorServer {
    private Map<String, Source> topicToSource;

    public ReplicatorServer() {
        this.topicToSource = new HashMap<>();
    }

    public synchronized Source createSource(String topic, SourceOptions options) {
        Source source = new Source(topic, options);
        Source oldSource = topicToSource.computeIfAbsent(topic, k -> source);

        if (source == oldSource) {
            source.init();
        }

        return oldSource;
    }

    public synchronized void deleteSource(String topic) {
        topicToSource.remove(topic);
    }

    public synchronized Source replaceSnapshotGenerator(String topic, SourceOptions options) {
        Source source = new Source(topic, options);
        source.init();

        topicToSource.put(topic, source);

        return source;
    }

    public synchronized Source getSource(String topic) {
        return topicToSource.get(topic);
    }
}
