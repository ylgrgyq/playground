package com.github.ylgrgyq.resender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestingProducerStorage implements ConsumerStorage, ProducerStorage {
    private final ArrayList<ElementWithId> producedPayloads;
    private long lastProducedId;
    private long lastCommittedId;
    private boolean stopped;

    public TestingProducerStorage() {
        this.producedPayloads = new ArrayList<>();
        this.lastProducedId = 0;
        this.lastCommittedId = -1;
    }

    synchronized List<ElementWithId> getProdcedPayloads() {
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
    public synchronized Collection<ElementWithId> read(long fromId, int limit) throws InterruptedException {
        ArrayList<ElementWithId> ret = new ArrayList<>();
        while (true) {
            while (producedPayloads.isEmpty()) {
                wait();
            }

            ElementWithId firstPayload = producedPayloads.get(0);
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
    public long getLastProducedId() {
        return lastProducedId;
    }

    @Override
    public synchronized void store(Collection<ElementWithId> batch) {
        for (ElementWithId elementWithId : batch) {
            producedPayloads.add(elementWithId);
            assert lastProducedId != elementWithId.getId() :
                    "lastProducedId: " + lastProducedId + " payloadId:" + elementWithId.getId();
            if (elementWithId.getId() > lastProducedId) {
                lastProducedId = elementWithId.getId();
            }
        }
        notify();
    }

    @Override
    public void close() throws Exception {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }
}
