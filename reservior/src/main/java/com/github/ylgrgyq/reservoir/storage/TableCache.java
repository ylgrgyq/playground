package com.github.ylgrgyq.reservoir.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.ylgrgyq.reservoir.ObjectWithId;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TableCache {
    private String baseDir;
    private Cache<Integer, Table> cache;

    TableCache(String baseDir) {
        this.baseDir = baseDir;
        cache = Caffeine.newBuilder()
                .maximumSize(1024)
                .build();
    }

    private Table findTable(int fileNumber, long fileSize) throws IOException {
        Table t = cache.getIfPresent(fileNumber);
        if (t == null) {
            t = loadTable(fileNumber, fileSize);
        }
        return t;
    }

    Table loadTable(int fileNumber, long fileSize) throws IOException {
        String tableFileName = FileName.getSSTableName(fileNumber);
        FileChannel ch = FileChannel.open(Paths.get(baseDir, tableFileName), StandardOpenOption.READ);
        Table t = Table.open(ch, fileSize);
        cache.put(fileNumber, t);
        return t;
    }

    SeekableIterator<Long, ObjectWithId> iterator(int fileNumber, long fileSize) throws IOException {
        Table t = findTable(fileNumber, fileSize);
        assert t != null;
        return t.iterator();
    }

    boolean hasTable(int fileNumber) {
        Table t = cache.getIfPresent(fileNumber);
        return t != null;
    }

    void evict(int fileNumber) throws IOException {
        Table t = cache.getIfPresent(fileNumber);
        if (t != null) {
            t.close();
        }
        cache.invalidate(fileNumber);
    }

    void evict(List<Integer> fileNumbers) throws IOException {
        for (int fileNumber : fileNumbers) {
            evict(fileNumber);
        }
    }

    void evictAll() throws IOException {
        for(Map.Entry<Integer, Table> e : cache.asMap().entrySet()) {
            e.getValue().close();
        }
        cache.invalidateAll();
    }

    Set<Integer> getAllFileNumbers() {
        return cache.asMap().keySet();
    }
}
