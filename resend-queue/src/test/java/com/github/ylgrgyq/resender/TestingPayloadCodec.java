package com.github.ylgrgyq.resender;

public class TestingPayloadCodec implements Codec<TestingPayload> {
    @Override
    public TestingPayload deserialize(byte[] bytes) throws DeserializationException {
        return new TestingPayload(bytes);
    }

    @Override
    public byte[] serialize(TestingPayload obj) throws SerializationException {
        return obj.getContent().clone();
    }
}
