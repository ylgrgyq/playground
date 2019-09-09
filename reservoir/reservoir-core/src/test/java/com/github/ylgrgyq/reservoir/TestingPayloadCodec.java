package com.github.ylgrgyq.reservoir;

import java.nio.ByteBuffer;

public class TestingPayloadCodec implements Codec<TestingPayload> {
    private static final int MINIMUM_LENGTH = Integer.BYTES + 1;

    @Override
    public TestingPayload deserialize(byte[] bytes) throws DeserializationException {
        if (bytes.length < MINIMUM_LENGTH) {
            throw new DeserializationException("buffer underflow, at least needs "
                    + MINIMUM_LENGTH + " bytes, actual: " + bytes.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        boolean valid = buffer.get() == (byte) 1;
        int len = buffer.getInt();
        byte[] content = new byte[len];
        buffer.get(content);

        return new TestingPayload(valid, content);
    }


    @Override
    public byte[] serialize(TestingPayload obj) throws SerializationException {
        byte[] bs = new byte[MINIMUM_LENGTH + obj.getContent().length];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.put(obj.isValid() ? (byte) 1 : (byte) 0);
        buffer.putInt(obj.getContent().length);
        buffer.put(obj.getContent());
        return buffer.array();
    }
}
