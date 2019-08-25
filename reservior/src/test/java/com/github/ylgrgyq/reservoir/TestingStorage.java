package com.github.ylgrgyq.reservoir;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestingStorage extends AbstractTestingStorage{
    private final ArrayList<ObjectWithId> producedPayloads;
    private long lastProducedId;
    private long lastCommittedId;
    private boolean closed;

    public TestingStorage() {
        this.producedPayloads = new ArrayList<>();
        this.lastProducedId = 0;
        this.lastCommittedId = -1;
    }

    synchronized List<ObjectWithId> getProdcedPayloads() {
        return new ArrayList<>(producedPayloads);
    }

    @Override
    public synchronized void commitId(long id) {
        lastCommittedId = id;
    }

    @Override
    public synchronized long getLastCommittedId() {
        return lastCommittedId;
    }

    @Override
    public synchronized List<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException {
        ArrayList<ObjectWithId> ret = new ArrayList<>();
        while (true) {
            while (producedPayloads.isEmpty()) {
                wait();
            }

            ObjectWithId firstPayload = producedPayloads.get(0);
            int start = (int) (fromId - firstPayload.getId()) + 1;
            start = Math.max(0, start);
            int end = Math.min(start + limit, producedPayloads.size());

            if (start < end) {
                ret.addAll(producedPayloads.subList(start, end));
            }

            if (ret.isEmpty()) {
                wait();
                continue;
            }

            return ret;
        }
    }

    @Override
    public synchronized List<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException {
        final long endNanos = System.nanoTime() + unit.toNanos(timeout);
        final ArrayList<ObjectWithId> ret = new ArrayList<>();
        while (true) {
            while (producedPayloads.isEmpty()) {
                long millisRemain = TimeUnit.NANOSECONDS.toMillis(endNanos - System.nanoTime());
                if (millisRemain <= 0) {
                    return ret;
                }

                wait(millisRemain);
            }

            ObjectWithId firstPayload = producedPayloads.get(0);
            int start = (int) (fromId - firstPayload.getId()) + 1;
            start = Math.max(0, start);
            int end = Math.min(start + limit, producedPayloads.size());

            if (start < end) {
                ret.addAll(producedPayloads.subList(start, end));
            }

            if (ret.isEmpty()) {
                long millisRemain = TimeUnit.NANOSECONDS.toMillis(endNanos - System.nanoTime());
                if (millisRemain <= 0) {
                    return ret;
                }

                wait(millisRemain);
                continue;
            }

            return ret;
        }
    }

    @Override
    public long getLastProducedId() {
        return lastProducedId;
    }

    @Override
    public synchronized void store(List<ObjectWithId> batch) {
        for (ObjectWithId objectWithId : batch) {
            doAdd(objectWithId);
        }
        notify();
    }

    synchronized void add(ObjectWithId obj) {
        doAdd(obj);
        notify();
    }

    @Override
    public void close() throws Exception {
        closed = true;
    }

    boolean closed() {
        return closed;
    }

    void clear() {
        producedPayloads.clear();
        lastProducedId = 0;
        lastCommittedId = -1;
    }

    private void doAdd(ObjectWithId obj) {
        producedPayloads.add(obj);
        assert lastProducedId != obj.getId() :
                "lastProducedId: " + lastProducedId + " payloadId:" + obj.getId();
        if (obj.getId() > lastProducedId) {
            lastProducedId = obj.getId();
        }
    }
}
