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
    private ConcurrentSkipListMap<Long, ObjectWithId> table;
    private int memSize;

    Memtable() {
        table = new ConcurrentSkipListMap<>();
    }

    void add(long k, ObjectWithId v) {
        assert k > 0;

        table.put(k, v);
        memSize += Long.BYTES + v.getObjectInBytes().length;
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

    ObjectWithId get(long k) {
        return table.get(k);
    }

    List<ObjectWithId> getEntries(long start, long end) {
        if (end < firstId() || start > lastId()) {
            return Collections.emptyList();
        }

        final SeekableIterator<Long, ObjectWithId> iter = iterator();
        iter.seek(start);

        final List<ObjectWithId> ret = new ArrayList<>();
        while (iter.hasNext()) {
            final ObjectWithId v = iter.next();
            if (v.getId() >= start && v.getId() < end) {
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
        private final ConcurrentNavigableMap<Long, ObjectWithId> innerMap;
        @Nullable
        private Map.Entry<Long, ObjectWithId> offset;

        Itr(ConcurrentNavigableMap<Long, ObjectWithId> innerMap) {
            this.innerMap = innerMap;
            this.offset = innerMap.firstEntry();
        }

        @Override
        public void seek(Long key) {
            offset = innerMap.higherEntry(key);
        }

        @Override
        public boolean hasNext() {
            return offset != null;
        }

        @Override
        public ObjectWithId next() {
            assert offset != null;
            ObjectWithId v = offset.getValue();
            offset = innerMap.higherEntry(v.getId());
            return v;
        }
    }
}
