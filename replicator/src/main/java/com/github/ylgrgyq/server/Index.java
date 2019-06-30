package com.github.ylgrgyq.server;

public class Index {
    private String topic;
    private long index;

    public Index(String topic, long index) {
        this.topic = topic;
        this.index = index;
    }

    public String getTopic() {
        return topic;
    }

    public long getIndex() {
        return index;
    }
}
