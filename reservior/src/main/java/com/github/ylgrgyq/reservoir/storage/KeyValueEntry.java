package com.github.ylgrgyq.reservoir.storage;

final class KeyValueEntry<K, V> {
    private final K key;
    private final V val;

    KeyValueEntry(K key, V val) {
        this.key = key;
        this.val = val;
    }

    K getKey() {
        return key;
    }

    V getVal() {
        return val;
    }
}
