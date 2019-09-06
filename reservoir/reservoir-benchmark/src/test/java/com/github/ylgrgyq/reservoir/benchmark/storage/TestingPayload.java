package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.github.ylgrgyq.reservoir.ObjectWithId;
import com.github.ylgrgyq.reservoir.SerializationException;
import com.github.ylgrgyq.reservoir.Verifiable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

public class TestingPayload implements Verifiable {
    private static AtomicLong idGenerator = new AtomicLong();
    private static TestingPayloadCodec codec = new TestingPayloadCodec();

    private boolean valid;
    private long id;
    private byte[] content;

    public TestingPayload() {
        this.content = ("Hello").getBytes(StandardCharsets.UTF_8);
        this.id = idGenerator.incrementAndGet();
        this.valid = true;
    }

    public TestingPayload(byte[] content) {
        this.content = Arrays.copyOf(content, content.length);
        this.id = idGenerator.incrementAndGet();
        this.valid = true;
    }

    public TestingPayload(long id, byte[] content) {
        this.content = Arrays.copyOf(content, content.length);
        this.id = id;
        this.valid = true;
    }

    public TestingPayload(long id, boolean valid, byte[] content) {
        this.content = Arrays.copyOf(content, content.length);
        this.id = id;
        this.valid = valid;
    }

    public long getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public ObjectWithId createObjectWithId() {
        try {
            return new ObjectWithId(id, codec.serialize(this));
        } catch (SerializationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public TestingPayload setValid(boolean valid) {
        this.valid = valid;
        return this;
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
                "id=" + id +
                "content=" + Base64.getEncoder().encodeToString(content) +
                '}';
    }
}
