package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.ObjectWithId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

final class Memtable implements Iterable<ObjectWithId> {
    private final ConcurrentSkipListMap<Long, byte[]> table;
    private int memSize;

    Memtable() {
        table = new ConcurrentSkipListMap<>();
    }

    void add(ObjectWithId val) {
        long k = val.getId();
        assert k > 0;
        byte[] v = val.getObjectInBytes();

        table.put(k, v);
        memSize += Long.BYTES + v.length;
    }

    long firstId() {
        if (table.isEmpty()) {
            return -1L;
        } else {
            return table.firstKey();
        }
    }

    long lastId() {
        if (table.isEmpty()) {
            return -1L;
        } else {
            return table.lastKey();
        }
    }

    boolean isEmpty() {
        return table.isEmpty();
    }

    List<ObjectWithId> getEntries(long start, int limit) {
        if (start > lastId()) {
            return Collections.emptyList();
        }

        final SeekableIterator<Long, ObjectWithId> iter = iterator();
        iter.seek(start);

        final List<ObjectWithId> ret = new ArrayList<>();
        while (iter.hasNext()) {
            final ObjectWithId v = iter.next();
            if (v.getId() > start && ret.size() < limit) {
                ret.add(v);
            } else {
                break;
            }
        }

        return ret;
    }

    int getMemoryUsedInBytes(){
        return memSize;
    }

    @Override
    public SeekableIterator<Long, ObjectWithId> iterator() {
        return new Itr(table.clone());
    }

    private static class Itr implements SeekableIterator<Long, ObjectWithId> {
        private final ConcurrentNavigableMap<Long, byte[]> innerMap;
        @Nullable
        private Map.Entry<Long, byte[]> offset;

        Itr(ConcurrentNavigableMap<Long, byte[]> innerMap) {
            this.innerMap = innerMap;
            this.offset = innerMap.firstEntry();
        }

        @Override
        public SeekableIterator<Long, ObjectWithId> seek(Long key) {
            offset = innerMap.higherEntry(key);
            return this;
        }

        @Override
        public boolean hasNext() {
            return offset != null;
        }

        @Override
        public ObjectWithId next() {
            assert offset != null;
            long id = offset.getKey();
            byte[] v = offset.getValue();
            offset = innerMap.higherEntry(id);
            return new ObjectWithId(id, v);
        }
    }
}
