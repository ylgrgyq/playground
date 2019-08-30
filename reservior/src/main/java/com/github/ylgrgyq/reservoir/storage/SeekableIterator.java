package com.github.ylgrgyq.reservoir.storage;

import java.util.Iterator;

interface SeekableIterator<K, E> extends Iterator<E>{
    void seek(K key);
}
