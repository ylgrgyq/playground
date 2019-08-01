package com.github.ylgrgyq.replicator.common;

import java.util.HashMap;
import java.util.Map;

public class SerializerManager {
    private Map<MessageType, Serializer> serializers;

    public SerializerManager() {
        this.serializers = new HashMap<>();
    }

    void registerSerializer(MessageType type, Serializer serializer) {
        serializers.put(type, serializer);
    }

    Serializer getSerializer(MessageType type) {
        return serializers.get(type);
    }
}
