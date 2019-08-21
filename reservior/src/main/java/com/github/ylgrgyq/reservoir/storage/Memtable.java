package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.ObjectWithId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Author: ylgrgyq
 * Date: 18/6/11
 */
class Memtable implements Iterable<ObjectWithId> {
    private ConcurrentSkipListMap<Long, ObjectWithId> table;
    private int memSize;

    Memtable() {
        table = new ConcurrentSkipListMap<>();
    }

    void add(long k, ObjectWithId v) {
//        assert k > 0;

        if (! table.isEmpty() && k <= table.lastKey()){
            long removeStartKeyNotInclusive = table.ceilingKey(k);
            table = new ConcurrentSkipListMap<>(table.subMap(-1L, removeStartKeyNotInclusive));

            memSize = recalculateCurrentMemoryUsedInBytes();
        }

        table.put(k, v);
        memSize += Long.BYTES + v.getObjectInBytes().length;
    }

    private int recalculateCurrentMemoryUsedInBytes() {
        int entrySize = table.values().stream().mapToInt(o -> o.getObjectInBytes().length).sum();
        return table.size() * Long.BYTES + entrySize;
    }

    Long firstKey() {
        if (table.isEmpty()) {
            return -1L;
        } else {
            return table.firstKey();
        }
    }

    Long lastKey() {
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
        if (end < firstKey() || start > lastKey()) {
            return Collections.emptyList();
        }

        SeekableIterator<Long, ObjectWithId> iter = iterator();
        iter.seek(start);

        List<ObjectWithId> ret = new ArrayList<>();
        while (iter.hasNext()) {
            ObjectWithId v = iter.next();
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
        private ConcurrentNavigableMap<Long, ObjectWithId> innerMap;
        private Map.Entry<Long, ObjectWithId> offset;

        Itr(ConcurrentNavigableMap<Long, ObjectWithId> innerMap) {
            this.innerMap = innerMap;
            this.offset = innerMap.firstEntry();
        }

        @Override
        public void seek(Long key) {
            offset = innerMap.ceilingEntry(key);
        }

        @Override
        public boolean hasNext() {
            return offset != null;
        }

        @Override
        public ObjectWithId next() {
            ObjectWithId v = offset.getValue();
            offset = innerMap.higherEntry(v.getId());
            return v;
        }
    }
}
