package com.github.ylgrgyq.reservoir;

public class BadTestingPayloadCodec implements Codec<TestingPayload, byte[]> {
    @Override
    public TestingPayload deserialize(byte[] serializedObj) throws DeserializationException {
        throw new DeserializationException("deserialization failed");
    }

    @Override
    public byte[] serialize(TestingPayload obj) throws SerializationException {
        throw new SerializationException("serialization failed");
    }
}
