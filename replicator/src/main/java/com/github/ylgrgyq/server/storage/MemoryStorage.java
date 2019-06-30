package com.github.ylgrgyq.server.storage;

import com.github.ylgrgyq.server.Index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryStorage implements Storage{
    private List<byte[]> datas;
    private long offsetIndex;

    public MemoryStorage() {
        this.datas = new ArrayList<>();
        offsetIndex = 0;
    }

    @Override
    public void init() {

    }

    @Override
    public void append(byte[] data) {
        datas.add(data);
    }

    @Override
    public List<byte[]> getEntries(Index fromIndex, int limit) {
        int index = (int)(fromIndex.getIndex() - offsetIndex);
        if (index < 0) {
            return Collections.emptyList();
        }

        if (index > datas.size()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(datas.subList(index, Math.min(index + limit, datas.size())));
    }

    @Override
    public void trimToIndex(Index toIndex) {
        long index = toIndex.getIndex();
        if (index < offsetIndex) {
            return;
        }

        if (index > offsetIndex + datas.size()) {
            datas = new ArrayList<>();
        }

        index = index - offsetIndex;
        datas = new ArrayList<>(datas.subList((int)index, datas.size()));
    }
}
