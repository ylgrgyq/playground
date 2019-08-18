package com.github.ylgrgyq.resender;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

public class TestingPayload implements Payload {
    private static AtomicLong idGenerator = new AtomicLong();

    private long id;
    private byte[] content;

    public TestingPayload() {
        this.content = ("Hello").getBytes(StandardCharsets.UTF_8);
        this.id = idGenerator.incrementAndGet();
    }

    public TestingPayload(byte[] content) {
        this.content = content;
        this.id = idGenerator.incrementAndGet();
    }

    public TestingPayload(long id, byte[] content) {
        this.content = content;
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public ElementWithId createPayloweWithId() {
        return new ElementWithId(id, content);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestingPayload payload = (TestingPayload) o;
        return Arrays.equals(getContent(), payload.getContent());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getContent());
    }

    @Override
    public String toString() {
        return "TestingPayload{" +
                "content=" + Base64.getEncoder().encodeToString(content) +
                '}';
    }
}
